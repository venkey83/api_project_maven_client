package org.mule.maven.exchange;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.sisu.Parameters;
import org.mule.maven.exchange.utils.NamingUtils;
import org.mule.maven.exchange.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

@Mojo(name = "rest-connect", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(goal = "rest-connect")
public class RestConnectPackageMojo extends AbstractMojo {

    private static final Map<String, String> EXTENSIONS_SPEC_FORMAT = Collections.unmodifiableMap(
            new HashMap<String, String>() {
                {
                    put("raml", "RAML");
                    put("yaml", "JsonOAS");
                    put("json", "YamlOAS");
                }
            });
    private static final List<String> SUPPORTED_TYPES = Arrays.asList("raml", "oas");
    private static final String REST_CONNECT_WORKDIR = "rest_connect_workdir";

    /**
     * Injected by Maven so that forked process can be
     * launched from the working directory of current maven project in a multi-module build.  Should not be user facing.
     */
    @Component
    private MavenProject project;

    @Component
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;

    @Component
    protected MavenProjectHelper helper;

    @Parameter(defaultValue = "raml")
    private String classifier;

    /**
     * must point to the main file of the spec (same value of the "main" attribute of the exchange.json file)
     */
    @Parameter(required = true)
    private String mainFile;

    /**
     * reference to the ZIP fat file that's self contained, if not provided it will be guessed based on {@link NamingUtils#getFullFileName(org.apache.maven.project.MavenProject, java.lang.String, java.lang.String)}
     */
    @Parameter
    private String fatApiFile;

    /**
     * property to skip the complete connector generation
     */
    @Parameter(property = "exchange.maven.disable.restConnect", defaultValue = "false")
    private boolean disableRestConnect;

    @Override
    public void execute() throws MojoExecutionException {
        if (disableRestConnect) {
            getLog().info("Disabling connector generation..");
            return;
        }

        if (!SUPPORTED_TYPES.contains(classifier)) {
            // can't generate a connector for fragment
            getLog().info(String.format("Skipping connector generation, [%s] not supported (supported classifiers are [%s])", classifier, String.join(",", SUPPORTED_TYPES)));
            return;
        }

        //looks for zip fat API file
        final String buildDirectory = project.getBuild().getDirectory();
        final File compressedFatApi = calculateFatApiFile(buildDirectory);
        if (!compressedFatApi.exists()) {
            getLog().warn(String.format("Cant generate connector, full API [%s] doesn't exist", compressedFatApi.getAbsolutePath()));
            return;
        }

        //prepares unzipped folder to execute rest-connect
        final File restConnectWorkDir = new File(buildDirectory, REST_CONNECT_WORKDIR);
        final File apiMainFile = mainFileInExplodedFolder(compressedFatApi, restConnectWorkDir);

        //execute rest-connect
        final File restConnectOutputDir = new File(restConnectWorkDir, "target");
        executeMojo(
                plugin(
                        groupId("org.mule.connectivity"),
                        artifactId("rest-connect"),
                        version("1.10.1")
                ),
                goal("raml2smartconnector"),
                configuration(
                        element(name("raml"), apiMainFile.getAbsolutePath()),
                        element(name("specFormat"), calculateRestConnectFormat()),
                        element(name("outputDir"), restConnectOutputDir.getAbsolutePath())
                ),
                executionEnvironment(
                        project,
                        mavenSession,
                        pluginManager
                )
        );

        //attach the generated mule-plugin from rest-connect
        final File mulePlugin = searchMulePlugin(restConnectOutputDir);
        if (mulePlugin != null && mulePlugin.exists()) {
            getLog().info("Connector successfully generated");
            helper.attachArtifact(project, "jar", "mule-plugin", mulePlugin);
        } else {
            throw new MojoExecutionException(String.format("Couldn't find any generated connector to attach under [%s] (didn't find [%s])",
                    restConnectOutputDir.getAbsolutePath(), compressedFatApi.getAbsolutePath()));
        }
    }

    /**
     * @return a file pointing to the compressed fat API, either by taking it from the parameterized {@link #fatApiFile},
     * or by doing a guessing in the current build directory.
     */
    private File calculateFatApiFile(String buildDirectory) {
        File result;
        if (fatApiFile != null) {
            result = new File(fatApiFile);
        } else {
            final String fullFileName = NamingUtils.getFullFileName(project, getClassifier(), getType());
            getLog().debug(String.format("Parameter 'fatApiFile' was null, guessing the fat API to [%s]", fullFileName));
            result = new File(buildDirectory, fullFileName);
        }
        return result;
    }

    /**
     * @param compressedFatApi   compressed file to unzip
     * @param restConnectWorkDir target folder to unzip the fat API
     * @return a file pointing to the mainFile in the exploded working directory
     * @throws MojoExecutionException if the mainFile is pointing to non-existing files
     */
    private File mainFileInExplodedFolder(File compressedFatApi, File restConnectWorkDir) throws MojoExecutionException {
        try {
            ZipUtils.unzip(compressedFatApi.getAbsolutePath(), restConnectWorkDir.getAbsolutePath());
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Cant unzip spec under [%s] to generate a connector", compressedFatApi.getAbsolutePath()), e);
        }
        final File apiMainFile = new File(restConnectWorkDir, mainFile);
        if (!apiMainFile.exists()) {
            throw new MojoExecutionException(String.format("The parameter 'mainFile' [%s] cannot be found in [%s]", mainFile, compressedFatApi.getAbsolutePath()));
        }
        return apiMainFile;
    }

    /**
     * @return the supported spec on rest-connect side by the extension of the parameterized {@link #mainFile}
     * (see {@link #EXTENSIONS_SPEC_FORMAT})
     * @throws MojoExecutionException if the extension is not supported, or there's no extension at all
     */
    private String calculateRestConnectFormat() throws MojoExecutionException {
        final int offset = mainFile.lastIndexOf('.');
        final String extension = (offset > 0 ? mainFile.substring(offset + 1) : "DEFAULT_ERROR_VALUE").toLowerCase();
        if (!EXTENSIONS_SPEC_FORMAT.containsKey(extension)) {
            throw new MojoExecutionException(String.format("The 'mainFile' parameter needs to have a valid extension, but found [%s] (accepted extensions are: [%s])",
                    extension, String.join(",", EXTENSIONS_SPEC_FORMAT.keySet())));
        }
        return EXTENSIONS_SPEC_FORMAT.get(extension);
    }

    private File searchMulePlugin(File file) {
        if (file.isDirectory()) {
            File[] arr = file.listFiles();
            for (File f : arr) {
                File found = searchMulePlugin(f);
                if (found != null)
                    return found;
            }
        } else {
            if (file.getName().endsWith("mule-plugin.jar")) {
                return file;
            }
        }
        return null;
    }

    public String getType() {
        return "zip";
    }

    public String getClassifier() {
        return classifier;
    }
}

package org.mule.maven.exchange;


import amf.ProfileName;
import amf.ProfileNames;
import amf.client.AMF;
import amf.client.environment.DefaultEnvironment;
import amf.client.environment.Environment;
import amf.client.model.document.BaseUnit;
import amf.client.parse.Oas20Parser;
import amf.client.parse.Raml08Parser;
import amf.client.parse.RamlParser;
import amf.client.validate.ValidationReport;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Mojo(name = "validate-api", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(goal = "validate-api")
public class ValidateApiMojo extends AbstractMojo {

    @Component
    private MavenProject project;

    @Parameter(defaultValue = "raml")
    private String classifier;

    /**
     * must point to the main file of the spec (same value of the "main" attribute of the exchange.json file)
     */
    @Parameter()
    private String mainFile;

    /**
     * reference to the directory that's self contained, if not provided it will be guessed based on {@link ApiProjectConstants#getFullApiDirectory(java.io.File)}
     */
    @Parameter
    private String fatApiDirectory;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File buildDirectory = new File(project.getBuild().getDirectory());
        //How do we validate raml-fragments
        if (classifier.equals("raml") || classifier.equals("oas")) {
            try {
                AMF.init().get();

                final Environment env = DefaultEnvironment.apply();

                /* Parsing Raml 10 with specified file returning future. */
                final BaseUnit result;
                final ProfileName profileName;
                final File ramlFile = new File(calculateFatDirectory(buildDirectory), this.mainFile);
                final String mainFileURL = ramlFile.toURI().toString();

                if (classifier.equals("raml")) {
                    final List<String> lines = Files.readAllLines(ramlFile.toPath(), Charset.forName("UTF-8"));
                    final String firstLine = lines.stream().filter(l -> !StringUtils.isBlank(l)).findFirst().orElse("");
                    if (firstLine.equalsIgnoreCase("#%RAML 0.8")) {
                        result = new Raml08Parser(env).parseFileAsync(mainFileURL).get();
                        profileName = ProfileNames.RAML08();
                    } else {
                        result = new RamlParser(env).parseFileAsync(mainFileURL).get();
                        profileName = ProfileNames.RAML();
                    }
                } else {
                    result = new Oas20Parser(env).parseFileAsync(mainFileURL).get();
                    profileName = ProfileNames.OAS();
                }

                /* Run RAML default validations on parsed unit (expects no errors). */
                final ValidationReport report = AMF.validate(result, profileName, profileName.messageStyle()).get();
                if (!report.conforms()) {
                    getLog().error(report.toString());
                    throw new MojoFailureException("Build Fail");
                }

            } catch (InterruptedException | ExecutionException | IOException e) {
                throw new MojoExecutionException("Internal error while validating.", e);
            }
        }
    }

    /**
     * @return a file pointing to the directory of the fat API, either by taking it from the parameterized {@link #fatApiDirectory},
     * or by doing a guessing in the current build directory.
     */
    private File calculateFatDirectory(File buildDirectory) {
        File result;
        if (fatApiDirectory != null) {
            result = new File(fatApiDirectory);
        } else {
            result = ApiProjectConstants.getFullApiDirectory(buildDirectory);
            getLog().debug(String.format("Parameter 'fatApiDirectory' was null, guessing the fat API to [%s]", result.getAbsolutePath()));
        }
        return result;
    }
}

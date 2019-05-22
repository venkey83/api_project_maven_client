package org.mule.maven.exchange;

import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.mule.maven.exchange.utils.NamingUtils;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mule.maven.exchange.ApiProjectConstants.getFullApiDirectory;

@Mojo(name = "package-api", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(goal = "package-api")
public class ExchangeApiPackagerMojo extends AbstractMojo {


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


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File sourceDirectory = new File(project.getBuild().getSourceDirectory());
        final File buildDirectory = new File(project.getBuild().getDirectory());
        final File apiZip = new File(buildDirectory, getFileName());
        createZip(sourceDirectory, new ApiSourceFileFilter(sourceDirectory, buildDirectory), apiZip);
        //create simple zip
        helper.attachArtifact(project, getType(), getClassifier(), apiZip);

        //create full zip
        final String fullFileName = NamingUtils.getFullFileName(project, getClassifier(), getType());
        final File fullApiZip = new File(buildDirectory, fullFileName);
        createZip(getFullApiDirectory(buildDirectory), TrueFileFilter.INSTANCE, fullApiZip);
        helper.attachArtifact(project, getType(), getClassifier() + "-full", fullApiZip);
    }


    private void createZip(File sourceDir, FileFilter fileFilter, File zipFile) throws MojoExecutionException {
        try {
            try (FileOutputStream fileWriter = new FileOutputStream(zipFile);
                 ZipOutputStream zip = new ZipOutputStream(fileWriter)) {
                addZipEntries(sourceDir, fileFilter, zip, null);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Exception while generating zip file", e);
        }
    }

    public String getType() {
        return "zip";
    }

    public String getClassifier() {
        return classifier;
    }

    private void addZipEntries(File sourceDir, FileFilter fileFilter, ZipOutputStream zip, String basePath) throws IOException {
        final File[] files = sourceDir.listFiles(fileFilter);
        if (files != null) {
            for (File file : files) {
                final String name = basePath != null ? basePath + "/" + file.getName() : file.getName();
                if (file.isDirectory()) {
                    addZipEntries(file, fileFilter, zip, name);
                } else {
                    try (FileInputStream in = new FileInputStream(file)) {
                        final byte[] buf = new byte[1024];
                        zip.putNextEntry(new ZipEntry(name));
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            zip.write(buf, 0, len);
                        }
                        zip.closeEntry();
                    }
                }
            }
        }
    }

    protected String getFileName() {
        return project.getBuild().getFinalName() + "-" + getClassifier() + "." + getType();
    }

}

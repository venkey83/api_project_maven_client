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

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

@Mojo(name = "package", requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(goal = "package")
public class ExchangeApiPackagerMojo extends AbstractMojo {

    public static final String EXCHANGE_MODULES = "exchange_modules";
    public static final String EXCHANGE_MODULES_TMP = ".exchange_modules_tmp";
    public static final String WORKDIR = "workdir";
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

    private String dependenciesDir = "node_modules_repository";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File sourceDirectory = new File(project.getBuild().getSourceDirectory());
        final String buildDirectory = project.getBuild().getDirectory();
        final File apiZip = new File(buildDirectory, getFileName());
        final FileFilter filter = new FileFilter() {

            @Override
            public boolean accept(File path) {
                if (path.equals(new File(buildDirectory))) {
                    return false;
                } else if (path.getParentFile().equals(sourceDirectory)) {
                    boolean ignore = path.getName().equals(EXCHANGE_MODULES) || path.getName().equals(EXCHANGE_MODULES_TMP) || path.getName().equals(".mvn");
                    return !ignore;
                } else {
                    return true;
                }
            }
        };

        createZip(sourceDirectory, filter, apiZip);
        //create zip
        helper.attachArtifact(project, getType(), getClassifier(), apiZip);

        //Download transitive dependencies

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version("2.0")
                ),
                goal("copy-dependencies"),
                configuration(
                        element(name("outputDirectory"), "${project.build.directory}/" + dependenciesDir),
                        element(name("useRepositoryLayout"), "true")
                ),
                executionEnvironment(
                        project,
                        mavenSession,
                        pluginManager
                )
        );

        //Unzip the ramls


        final File fullApiZip = new File(buildDirectory, getFullFileName());
        final File workdir = new File(buildDirectory, WORKDIR);
        unzipDependenciesAndCopyTo(new File(buildDirectory, dependenciesDir), new File(workdir, EXCHANGE_MODULES));
        createZip(sourceDirectory, filter, workdir, TrueFileFilter.INSTANCE, fullApiZip);
        helper.attachArtifact(project, getType(), getClassifier() + "-full", fullApiZip);
    }

    private void unzipDependenciesAndCopyTo(File sourceFile, File targetFile) throws MojoExecutionException {
        try {
            if (sourceFile.isDirectory()) {
                File[] listFiles = sourceFile.listFiles();
                if (listFiles != null) {
                    for (File childFile : listFiles) {
                        unzipDependenciesAndCopyTo(childFile, new File(targetFile, childFile.getName()));
                    }
                }
            } else if (sourceFile.isFile() && sourceFile.getName().endsWith(".zip")) {
                File targetDirectory = targetFile.getParentFile();
                targetDirectory.mkdirs();
                byte[] buffer = new byte[1024];
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceFile))) {

                    ZipEntry zipEntry = zis.getNextEntry();
                    while (zipEntry != null) {
                        File newFile = new File(targetDirectory, zipEntry.getName());
                        try (FileOutputStream fos = new FileOutputStream(newFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        zipEntry = zis.getNextEntry();
                    }
                    zis.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to unzip " + sourceFile.getAbsolutePath(), e);
        }
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

    private void createZip(File rootDir, FileFilter rootFileFilter, File additionalRoot, FileFilter additionalFilter, File zipFile) throws MojoExecutionException {
        try {
            try (FileOutputStream fileWriter = new FileOutputStream(zipFile);
                 ZipOutputStream zip = new ZipOutputStream(fileWriter)) {
                addZipEntries(rootDir, rootFileFilter, zip, null);
                addZipEntries(additionalRoot, additionalFilter, zip, null);
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

    protected String getFullFileName() {
        return project.getBuild().getFinalName() + "-" + getClassifier() + "-full" + "." + getType();
    }
}

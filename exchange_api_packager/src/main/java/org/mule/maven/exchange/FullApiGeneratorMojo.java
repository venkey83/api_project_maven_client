package org.mule.maven.exchange;

import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.mule.maven.exchange.ApiProjectConstants.*;
import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

@Mojo(name = "generate-full-api", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@Execute(goal = "generate-full-api")
public class FullApiGeneratorMojo extends AbstractMojo {


    @Component
    private MavenProject project;

    @Component
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        //Download transitive dependencies

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version("2.0")
                ),
                goal("copy-dependencies"),
                configuration(
                        element(name("outputDirectory"), "${project.build.directory}/" + EXCHANGE_MODULES_REPOSITORY),
                        element(name("useRepositoryLayout"), "true")
                ),
                executionEnvironment(
                        project,
                        mavenSession,
                        pluginManager
                )
        );

        final File buildDirectory = new File(project.getBuild().getDirectory());
        final File fullApiDirectory = getFullApiDirectory(buildDirectory);
        final File sourceDirectory = new File(project.getBuild().getSourceDirectory());
        try {
            unzipDependenciesAndCopyTo(new File(buildDirectory, EXCHANGE_MODULES_REPOSITORY), new File(fullApiDirectory, EXCHANGE_MODULES));
            FileUtils.copyDirectory(sourceDirectory, fullApiDirectory, new ApiSourceFileFilter(sourceDirectory, buildDirectory), true);
        } catch (IOException e) {
            throw new MojoExecutionException("Exception while trying to copy sources for `exchange-generate-full-api`", e);
        }

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
}

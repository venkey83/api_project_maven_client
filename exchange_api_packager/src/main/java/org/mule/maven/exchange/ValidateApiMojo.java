package org.mule.maven.exchange;


import amf.MessageStyle;
import amf.MessageStyles;
import amf.ProfileName;
import amf.ProfileNames;
import amf.client.AMF;
import amf.client.environment.DefaultEnvironment;
import amf.client.environment.Environment;
import amf.client.model.document.BaseUnit;
import amf.client.parse.Oas20Parser;
import amf.client.parse.RamlParser;
import amf.client.validate.ValidationReport;
import amf.client.validate.ValidationResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
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
                final MessageStyle messageStyle;
                final String mainFile = new File(calculateFatDirectory(buildDirectory), this.mainFile).toURI().toString();


                if (classifier.equals("raml")) {
                    result = new RamlParser(env).parseFileAsync(mainFile).get();
                    profileName = ProfileNames.RAML();
                    messageStyle = MessageStyles.RAML();
                } else {
                    result = new Oas20Parser(env).parseFileAsync(mainFile).get();
                    profileName = ProfileNames.OAS();
                    messageStyle = MessageStyles.OAS();
                }

                /* Run RAML default validations on parsed unit (expects no errors). */
                final ValidationReport report = AMF.validate(result, profileName, messageStyle).get();
                if (!report.conforms()) {
                    final List<ValidationResult> results = report.results();
                    for (ValidationResult validationResult : results) {
                        getLog().error(validationResult.message());
                    }
                    throw new MojoFailureException("Build Fail");
                }

            } catch (InterruptedException | ExecutionException e) {
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

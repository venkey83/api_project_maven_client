package org.mule.maven.exchange.utils;

import org.apache.maven.project.MavenProject;

public class NamingUtils {

    /**
     * Provides the name of the ZIP file that has the self contained API (with all of its dependencies)
     * @param project working project
     * @param classifier artifact's classifier
     * @param type artifact's type
     * @return the name of the fat file for the ZIP file, such as "american-flights-api-1.0.3-raml-full.zip"
     */
    public static String getFullFileName(MavenProject project, final String classifier, final String type) {
        return project.getBuild().getFinalName() + "-" + "fat-" + classifier + "." + type;
    }
}

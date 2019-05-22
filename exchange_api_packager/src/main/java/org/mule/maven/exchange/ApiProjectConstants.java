package org.mule.maven.exchange;

import java.io.File;

public class ApiProjectConstants {

    public static final String EXCHANGE_MODULES = "exchange_modules";
    public static final String EXCHANGE_MODULES_TMP = ".exchange_modules_tmp";
    public static final String API_EXPANDED = "full_api";
    public static final String EXCHANGE_MODULES_REPOSITORY = "exchange_modules_repository";

    public static File getFullApiDirectory(File buildDirectory) {
        return new File(buildDirectory, API_EXPANDED);
    }
}

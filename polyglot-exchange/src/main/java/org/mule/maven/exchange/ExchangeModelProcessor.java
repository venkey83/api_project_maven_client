package org.mule.maven.exchange;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.building.ModelSource2;
import org.apache.maven.model.io.ModelParseException;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.mule.maven.exchange.model.ExchangeDependency;
import org.mule.maven.exchange.model.ExchangeModel;
import org.mule.maven.exchange.model.ExchangeModelSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

@Component(role = ModelProcessor.class)
public class ExchangeModelProcessor implements ModelProcessor {

    private static Logger LOGGER = Logger.getLogger(ExchangeModelProcessor.class.getName());

    private static final String JSON_EXT = ".json";

    public static final String PACKAGER_VERSION = "1.0-SNAPSHOT";

    private ExchangeModelSerializer objectMapper = new ExchangeModelSerializer();

    @Requirement
    private ModelReader modelReader;

    public Model read(File file, Map<String, ?> map) throws IOException, ModelParseException {
        return read(new FileInputStream(file), map);
    }


    public Model read(InputStream inputStream, Map<String, ?> map) throws IOException, ModelParseException {
        return read(new InputStreamReader(inputStream, StandardCharsets.UTF_8), map);
    }

    public Model read(Reader reader, Map<String, ?> options) throws IOException, ModelParseException {

        Object source = (options != null) ? options.get(SOURCE) : null;
        if (source instanceof ModelSource2 && ((ModelSource2) source).getLocation().endsWith(JSON_EXT)) {
            final String location = ((ModelSource2) source).getLocation();
            final ExchangeModel model = objectMapper.read(reader);
            boolean modified = false;
            if (StringUtils.isBlank(model.getAssetId())) {
                model.setAssetId(dasherize(model.getName()));
                modified = true;
            }
            if (StringUtils.isBlank(model.getVersion())) {
                model.setVersion("1.0.0-SNAPSHOT");
                modified = true;
            }
            if (StringUtils.isBlank(model.getGroupId())) {
                final String orgId = guessOrgId(location);
                if (orgId != null) {
                    model.setGroupId(orgId);
                    modified = true;
                } else {
                    throw new RuntimeException("No `groupId` on exchange json or System property `groupId` or being an apivcs project");
                }
            }

            if (modified) {
                LOGGER.log(Level.WARNING, "[WARNING] exchange.json was modified by the build.");
                objectMapper.write(model, new File(location));
            }

            final Model mavenModel = toMavenModel(model);
            if (Boolean.getBoolean("exchange.maven.debug")) {
                System.out.println("Maven Model \n" + toXmlString(mavenModel));
            }
            return mavenModel;
        } else {
            //It's a normal maven project with a pom.xml file
            //It's a normal maven project with a pom.xml file
            return modelReader.read(reader, options);
        }
    }

    private String guessOrgId(String location) {
        String groupId = System.getProperty("groupId");
        if (groupId == null) {
            final File projectFolder = new File(location).getParentFile();
            final File apiVcsConfigFile = new File(new File(projectFolder, ".apivcs"), "config.properties");
            if (apiVcsConfigFile.exists()) {
                final Properties properties = new Properties();
                try (final FileInputStream fileInputStream = new FileInputStream(apiVcsConfigFile)) {
                    properties.load(fileInputStream);
                } catch (IOException e) {

                }
                groupId = properties.getProperty("orgId");
            }
        }

        return groupId;
    }

    public String toXmlString(Model mavenModel) throws IOException {
        StringWriter stringWriter = new StringWriter();
        new MavenXpp3Writer().write(stringWriter, mavenModel);
        return stringWriter.toString();
    }

    private Model toMavenModel(ExchangeModel model) {
        final Model result = new Model();
        result.setModelVersion("4.0.0");
        result.setArtifactId(model.getAssetId());
        result.setGroupId(model.getGroupId());
        result.setName(model.getName());
        result.setVersion(model.getVersion());
        result.setRepositories(singletonList(createExchangeRepository()));
        final List<Dependency> dependencies = model.getDependencies().stream().map(this::toMavenDependency).collect(Collectors.toList());
        result.setDependencies(dependencies);
        final Build build = new Build();
        build.setDirectory("${project.basedir}/.exchange_modules_tmp/target");
        build.setSourceDirectory("${project.basedir}");
        build.addPlugin(createPackagerPlugin(model));
        result.setBuild(build);
        return result;
    }

    private String dasherize(String name) {
        return name.toLowerCase().replaceAll(" ", "-");
    }

    private Plugin createPackagerPlugin(ExchangeModel model) {
        Plugin result = new Plugin();
        result.setGroupId("org.mule.maven.exchange");
        result.setArtifactId("exchange_api_packager");
        result.setVersion(PACKAGER_VERSION);
        final Xpp3Dom configuration = new Xpp3Dom("configuration");
        final Xpp3Dom classifierNode = new Xpp3Dom("classifier");
        classifierNode.setValue(model.getClassifier());
        configuration.addChild(classifierNode);
        final Xpp3Dom mainFileNode = new Xpp3Dom("mainFile");
        mainFileNode.setValue(model.getMain());
        configuration.addChild(mainFileNode);
        result.setConfiguration(configuration);

        PluginExecution generateSources = new PluginExecution();
        generateSources.setId("generate-full-api");
        generateSources.setPhase("generate-sources");
        generateSources.addGoal("generate-full-api");
        result.addExecution(generateSources);

        PluginExecution compilePhase = new PluginExecution();
        compilePhase.setId("validate-api");
        compilePhase.setPhase("compile");
        compilePhase.addGoal("validate-api");
        result.addExecution(compilePhase);


        PluginExecution packagePhase = new PluginExecution();
        packagePhase.setId("generate-artifacts");
        packagePhase.setPhase("package");
        packagePhase.addGoal("package-api");
        packagePhase.addGoal("rest-connect");
        result.addExecution(packagePhase);
        return result;
    }

    private Dependency toMavenDependency(ExchangeDependency dep) {
        Dependency result = new Dependency();
        result.setArtifactId(dep.getAssetId());
        result.setGroupId(dep.getGroupId());
        result.setVersion(dep.getVersion());
        result.setClassifier("raml-fragment");
        result.setType("zip");
        return result;
    }

    // <repository>
//     <id>anypoint-exchange-v2</id>
//     <name>Anypoint Exchange</name>
//     <url>https://maven.anypoint.mulesoft.com/api/v2/maven</url>
//    <layout>default</layout>
// </repository>
    private Repository createExchangeRepository() {
        Repository repository = new Repository();
        repository.setId("anypoint-exchange-v2");
        repository.setName("Anypoint Exchange");
        repository.setUrl("https://maven.anypoint.mulesoft.com/api/v2/maven");
        repository.setLayout("default");
        return repository;
    }

    public File locatePom(File projectDirectory) {
        return new File(projectDirectory, "exchange.json");
    }
}

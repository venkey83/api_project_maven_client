# API Project Maven Polyglot

This project contains the maven polyglot extension and the maven packager to build an API project using maven.

As an example go to the api_example and just use 
`mvn clean install`

This will call our [polyglot](https://www.baeldung.com/maven-polyglot) extension `ExchangeModelProcessor.java` that translate the exchange.json into 
a maven model. This maven model, basically a pom.xml file, will contain the api packager plugin that generates the api artifactes, (raml, raml-artifact, oas) with the -full conterpart.

To debug the generated pom.xml run with -Dexchange.maven.debug=true
`mvn clean install -Dexchange.maven.debug=true`
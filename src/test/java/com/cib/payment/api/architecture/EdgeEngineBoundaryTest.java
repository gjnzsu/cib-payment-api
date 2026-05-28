package com.cib.payment.api.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EdgeEngineBoundaryTest {
    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final List<String> FI_DOMAIN_FORBIDDEN_DEPENDENCIES = List.of(
            "com.cib.payment.api.api",
            "com.cib.payment.api.application",
            "com.cib.payment.api.infrastructure",
            "java.net.http",
            "java.sql",
            "javax.persistence",
            "javax.servlet",
            "javax.sql",
            "javax.ws.rs",
            "javax.xml",
            "javax.xml.bind",
            "jakarta.persistence",
            "jakarta.servlet",
            "jakarta.ws.rs",
            "jakarta.xml",
            "jakarta.xml.bind",
            "org.apache.cxf",
            "org.apache.http",
            "org.apache.xerces",
            "org.apache.xmlbeans",
            "org.dom4j",
            "org.hibernate",
            "org.jdom",
            "org.jooq",
            "org.openapitools",
            "org.springdoc",
            "org.springframework",
            "org.w3c.dom",
            "org.xml.sax",
            "com.fasterxml.jackson.dataformat.xml",
            "io.swagger",
            "io.swagger.v3",
            "feign",
            "nu.xom",
            "okhttp3",
            "retrofit2");
    private static final String APPLICATION_PORT_PACKAGE = "com.cib.payment.api.application.port";
    private static final List<String> REPOSITORY_FORBIDDEN_DEPENDENCIES = List.of(
            "com.cib.payment.api.application.port.FiPaymentRepository",
            "com.cib.payment.api.application.port.RecallInvestigationRepository",
            "com.cib.payment.api.application.port.PaymentStatusRepository",
            "com.cib.payment.api.application.port.IdempotencyRepository",
            "com.cib.payment.api.application.port.PaymentEngineRecordRepository",
            "com.cib.payment.api.infrastructure.persistence");
    private static final List<String> REPOSITORY_PORT_TYPES = List.of(
            "FiPaymentRepository",
            "RecallInvestigationRepository",
            "PaymentStatusRepository",
            "IdempotencyRepository",
            "PaymentEngineRecordRepository");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([^;]+);");
    private static final Pattern PACKAGE_REFERENCE_PATTERN = Pattern.compile(
            "\\b(?:com\\.cib\\.payment\\.api|java\\.net\\.http|java\\.sql|javax\\.(?:persistence|servlet|sql|ws\\.rs|xml)"
                    + "|jakarta\\.(?:persistence|servlet|ws\\.rs|xml)|org\\.(?:apache\\.(?:cxf|http|xerces|xmlbeans)|dom4j"
                    + "|hibernate|jdom|jooq|openapitools|springdoc|springframework|w3c\\.dom|xml\\.sax)"
                    + "|com\\.fasterxml\\.jackson\\.dataformat\\.xml|io\\.swagger(?:\\.v3)?|feign|nu\\.xom|okhttp3|retrofit2)"
                    + "(?:\\.[A-Za-z_$][\\w$]*)*");

    @Test
    void controllersDoNotAccessRepositories() throws IOException {
        for (var source : javaSources("com/cib/payment/api/api/controller")) {
            assertNoForbiddenDependencies(
                    source,
                    REPOSITORY_FORBIDDEN_DEPENDENCIES,
                    "%s must delegate persistence through application services");
        }
    }

    @Test
    void edgeApplicationServicesDoNotAccessEngineRepositories() throws IOException {
        for (var source : javaSources("com/cib/payment/api/application/service")) {
            assertThat(read(source))
                    .as("%s must use engine ports, not engine repositories", source)
                    .doesNotContain("PaymentEngineRecordRepository")
                    .doesNotContain("InMemoryPaymentEngineRecordRepository")
                    .doesNotContain("infrastructure.engine.InMemoryPaymentEngineRecordRepository");
        }
    }

    @Test
    void engineImplementationDoesNotDependOnApiControllerOrDtoClasses() throws IOException {
        for (var source : javaSources("com/cib/payment/api/infrastructure/engine")) {
            assertThat(read(source))
                    .as("%s must not depend on API adapter classes", source)
                    .doesNotContain("com.cib.payment.api.api.controller")
                    .doesNotContain("com.cib.payment.api.api.dto");
        }
    }

    @Test
    void apiControllersDoNotReturnInternalPacs008Representations() throws IOException {
        for (var source : javaSources("com/cib/payment/api/api/controller")) {
            assertThat(read(source))
                    .as("%s must not expose internal pacs.008 transfer objects", source)
                    .doesNotContain("InternalInterbankTransfer")
                    .doesNotContain("pacs008")
                    .doesNotContain("pacs.008");
        }
    }

    @Test
    void fiControllersDoNotAccessFiRepositoriesDirectly() throws IOException {
        for (var source : optionalJavaSources("com/cib/payment/api/api/controller", "FiPaymentController.java")) {
            assertNoForbiddenDependencies(
                    source,
                    List.of(
                            "com.cib.payment.api.application.port.FiPaymentRepository",
                            "com.cib.payment.api.application.port.RecallInvestigationRepository",
                            "com.cib.payment.api.infrastructure.persistence.InMemoryFiPaymentRepository",
                            "com.cib.payment.api.infrastructure.persistence.InMemoryRecallInvestigationRepository"),
                    "%s must delegate FI persistence through application services");
        }
    }

    @Test
    void fiDomainModelsDoNotDependOnApiHttpSpringOrXmlClasses() throws IOException {
        for (var source : domainModelSources()) {
            assertNoForbiddenDependencies(
                    source,
                    FI_DOMAIN_FORBIDDEN_DEPENDENCIES,
                    "%s must stay framework, adapter, persistence, HTTP, and XML-parser independent");
        }
    }

    private List<Path> javaSources(String packagePath) throws IOException {
        var root = MAIN_SOURCES.resolve(packagePath);
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private List<Path> domainModelSources() throws IOException {
        var sources = javaSources("com/cib/payment/api/domain/model");
        assertThat(sources)
                .as("FI domain guardrail must scan the domain model surface that contains FI models")
                .isNotEmpty();
        return sources;
    }

    private List<Path> optionalJavaSources(String packagePath, String fileName) throws IOException {
        return javaSources(packagePath).stream()
                .filter(path -> path.getFileName().toString().equals(fileName))
                .toList();
    }

    private void assertNoForbiddenDependencies(Path source, List<String> forbiddenDependencies, String message)
            throws IOException {
        var dependencies = dependenciesIn(source);
        var forbidden = dependencies.stream()
                .filter(dependency -> forbiddenDependencies.stream().anyMatch(forbiddenDependency ->
                        dependency.equals(forbiddenDependency) || dependency.startsWith(forbiddenDependency + ".")))
                .toList();

        assertThat(forbidden)
                .as(message, source)
                .isEmpty();
    }

    private Set<String> dependenciesIn(Path source) throws IOException {
        var stripped = stripCommentsAndStringLiterals(read(source));
        var dependencies = new ArrayList<String>();
        var wildcardImports = new ArrayList<String>();

        var imports = IMPORT_PATTERN.matcher(stripped);
        while (imports.find()) {
            var importedType = imports.group(1);
            if (importedType.endsWith(".*")) {
                var importedPackage = importedType.substring(0, importedType.length() - 2);
                dependencies.add(importedPackage);
                wildcardImports.add(importedPackage);
            } else {
                dependencies.add(importedType);
            }
        }

        var packageReferences = PACKAGE_REFERENCE_PATTERN.matcher(stripped);
        while (packageReferences.find()) {
            dependencies.add(packageReferences.group());
        }

        if (wildcardImports.contains(APPLICATION_PORT_PACKAGE)) {
            REPOSITORY_PORT_TYPES.stream()
                    .filter(repositoryType -> Pattern.compile("\\b" + repositoryType + "\\b").matcher(stripped).find())
                    .map(repositoryType -> APPLICATION_PORT_PACKAGE + "." + repositoryType)
                    .forEach(dependencies::add);
        }

        return Set.copyOf(dependencies);
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String stripCommentsAndStringLiterals(String source) {
        var stripped = new StringBuilder(source.length());
        var inLineComment = false;
        var inBlockComment = false;
        var inString = false;
        var inTextBlock = false;
        var inChar = false;

        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            var third = index + 2 < source.length() ? source.charAt(index + 2) : '\0';

            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                    stripped.append(current);
                } else {
                    stripped.append(' ');
                }
                continue;
            }

            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    stripped.append("  ");
                    index++;
                } else {
                    stripped.append(current == '\n' ? '\n' : ' ');
                }
                continue;
            }

            if (inTextBlock) {
                if (current == '"' && next == '"' && third == '"') {
                    inTextBlock = false;
                    stripped.append("   ");
                    index += 2;
                } else {
                    stripped.append(current == '\n' ? '\n' : ' ');
                }
                continue;
            }

            if (inString) {
                if (current == '\\' && next != '\0') {
                    stripped.append("  ");
                    index++;
                } else if (current == '"') {
                    inString = false;
                    stripped.append(' ');
                } else {
                    stripped.append(current == '\n' ? '\n' : ' ');
                }
                continue;
            }

            if (inChar) {
                if (current == '\\' && next != '\0') {
                    stripped.append("  ");
                    index++;
                } else if (current == '\'') {
                    inChar = false;
                    stripped.append(' ');
                } else {
                    stripped.append(current == '\n' ? '\n' : ' ');
                }
                continue;
            }

            if (current == '/' && next == '/') {
                inLineComment = true;
                stripped.append("  ");
                index++;
            } else if (current == '/' && next == '*') {
                inBlockComment = true;
                stripped.append("  ");
                index++;
            } else if (current == '"' && next == '"' && third == '"') {
                inTextBlock = true;
                stripped.append("   ");
                index += 2;
            } else if (current == '"') {
                inString = true;
                stripped.append(' ');
            } else if (current == '\'') {
                inChar = true;
                stripped.append(' ');
            } else {
                stripped.append(current);
            }
        }

        return stripped.toString();
    }
}

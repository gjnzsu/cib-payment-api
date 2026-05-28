package com.cib.payment.api.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EdgeEngineBoundaryTest {
    private static final Path MAIN_SOURCES = Path.of("src/main/java");

    @Test
    void controllersDoNotAccessRepositories() throws IOException {
        for (var source : javaSources("com/cib/payment/api/api/controller")) {
            assertThat(read(source))
                    .as("%s must delegate persistence through application services", source)
                    .doesNotContain("Repository")
                    .doesNotContain("InMemory");
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
        for (var source : javaSources("com/cib/payment/api/api/controller")) {
            assertThat(read(source))
                    .as("%s must delegate FI persistence through application services", source)
                    .doesNotContain("FiPaymentRepository")
                    .doesNotContain("RecallInvestigationRepository")
                    .doesNotContain("InMemoryFiPaymentRepository")
                    .doesNotContain("InMemoryRecallInvestigationRepository");
        }
    }

    @Test
    void fiDomainModelsDoNotDependOnApiHttpSpringOrXmlClasses() throws IOException {
        for (var source : fiDomainSources()) {
            assertThat(read(source))
                    .as("%s must stay framework and adapter independent", source)
                    .doesNotContain("com.cib.payment.api.api")
                    .doesNotContain("jakarta.servlet")
                    .doesNotContain("javax.servlet")
                    .doesNotContain("org.springframework")
                    .doesNotContain("org.w3c.dom")
                    .doesNotContain("javax.xml")
                    .doesNotContain("jakarta.xml")
                    .doesNotContain("org.xml.sax");
        }
    }

    private List<Path> javaSources(String packagePath) throws IOException {
        var root = MAIN_SOURCES.resolve(packagePath);
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private List<Path> fiDomainSources() throws IOException {
        return javaSources("com/cib/payment/api/domain/model").stream()
                .filter(path -> {
                    var fileName = path.getFileName().toString();
                    return fileName.startsWith("Fi")
                            || fileName.startsWith("RecallInvestigation")
                            || fileName.equals("AccountRelationshipRole.java")
                            || fileName.equals("CorrespondentSettlementContext.java");
                })
                .toList();
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

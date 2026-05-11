package com.cib.payment.api.infrastructure.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KubernetesManifestTest {
    private static final Path K8S_DIR = Path.of("k8s");

    @Test
    void deploymentDefinesWorkloadImagePortsAndHealthProbes() throws Exception {
        var manifest = Files.readString(K8S_DIR.resolve("deployment.yaml"), StandardCharsets.UTF_8);

        assertThat(manifest).contains("kind: Deployment");
        assertThat(manifest).contains("name: domestic-rtp-payment-api");
        assertThat(manifest).contains("containerPort: 8080");
        assertThat(manifest).contains("path: /actuator/health/readiness");
        assertThat(manifest).contains("path: /actuator/health/liveness");
        assertThat(manifest).contains("port: http");
    }

    @Test
    void serviceExposesTheApplicationPort() throws Exception {
        var manifest = Files.readString(K8S_DIR.resolve("service.yaml"), StandardCharsets.UTF_8);

        assertThat(manifest).contains("kind: Service");
        assertThat(manifest).contains("name: domestic-rtp-payment-api");
        assertThat(manifest).contains("port: 80");
        assertThat(manifest).contains("targetPort: http");
    }

    @Test
    void gatewayRoutesApiAndDocumentationPathsToServiceWithoutCustomGatewayDeployment() throws Exception {
        var manifest = Files.readString(K8S_DIR.resolve("gateway.yaml"), StandardCharsets.UTF_8);

        assertThat(manifest).contains("kind: Gateway");
        assertThat(manifest).contains("kind: HTTPRoute");
        assertThat(manifest).contains("gateway.networking.k8s.io/v1");
        assertThat(manifest).contains("name: domestic-rtp-payment-api");
        assertThat(manifest).contains("value: /v1/domestic-payments");
        assertThat(manifest).contains("value: /swagger-ui");
        assertThat(manifest).contains("value: /openapi");
        assertThat(manifest).contains("port: 80");
        assertThat(manifest).doesNotContain("api-gateway");
    }
}

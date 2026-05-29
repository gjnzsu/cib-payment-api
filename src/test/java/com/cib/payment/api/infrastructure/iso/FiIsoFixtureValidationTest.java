package com.cib.payment.api.infrastructure.iso;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FiIsoFixtureValidationTest {
    private static final Path FI_FIXTURES = Path.of("src", "test", "resources", "fi");
    private static final String PACS_009_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08";
    private static final String CAMT_056_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:camt.056.001.08";
    private static final String CAMT_029_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:camt.029.001.09";
    private static final String FI_CLIENT_ID = "FICLIENT01";
    private static final String ORIGINAL_PAYMENT_REFERENCE = "FI-E2E-20260528-0001";
    private static final Set<String> PACS_009_FIXTURES = Set.of(
            "pacs009-accepted-nostro.xml",
            "pacs009-pending-vostro.xml",
            "pacs009-rejected-loro.xml",
            "pacs009-non-usd.xml",
            "pacs009-missing-instructed-agent.xml",
            "pacs009-unsafe.xml");
    private static final Set<String> CAMT_056_FIXTURES = Set.of(
            "camt056-recall-accepted.xml",
            "camt056-recall-rejected.xml",
            "camt056-investigation-pending.xml",
            "camt056-wrong-original-reference.xml",
            "camt056-malformed.xml",
            "camt056-unsafe.xml");
    private static final Set<String> CAMT_029_FIXTURES = Set.of(
            "camt029-accepted.xml",
            "camt029-rejected.xml",
            "camt029-pending.xml");
    private static final Pattern PAN_LIKE_VALUE = Pattern.compile("\\b\\d{16}\\b");
    private static final Pattern REAL_CUSTOMER_LOOKING_NAME =
            Pattern.compile("\\b[A-Z][a-z]+\\s+[A-Z][a-z]+\\b");

    @Test
    void fiIsoFixturesUseExpectedNamespacesAndSyntheticSafeValues() throws Exception {
        assertThat(Files.list(FI_FIXTURES).map(path -> path.getFileName().toString()))
                .containsExactlyInAnyOrderElementsOf(allExpectedFixtures());

        for (var fixture : PACS_009_FIXTURES) {
            var xml = readFixture(fixture);

            assertThat(xml).contains(PACS_009_NAMESPACE);
            assertThat(xml).contains(FI_CLIENT_ID, ORIGINAL_PAYMENT_REFERENCE);
            if (!fixture.equals("pacs009-non-usd.xml")) {
                assertThat(xml).contains("Ccy=\"USD\"");
            }
            assertNoObviousRealOrSensitiveValues(xml);
            assertUnsafeFixtureShape(fixture, xml);
        }

        for (var fixture : CAMT_056_FIXTURES) {
            var xml = readFixture(fixture);

            assertThat(xml).contains(CAMT_056_NAMESPACE);
            assertThat(xml).contains(FI_CLIENT_ID);
            if (!fixture.equals("camt056-wrong-original-reference.xml")) {
                assertThat(xml).contains(ORIGINAL_PAYMENT_REFERENCE);
            }
            assertNoObviousRealOrSensitiveValues(xml);
            assertUnsafeFixtureShape(fixture, xml);
        }

        for (var fixture : CAMT_029_FIXTURES) {
            var xml = readFixture(fixture);

            assertThat(xml).contains(CAMT_029_NAMESPACE);
            assertThat(xml).contains(FI_CLIENT_ID, ORIGINAL_PAYMENT_REFERENCE);
            assertNoObviousRealOrSensitiveValues(xml);
        }
    }

    private Set<String> allExpectedFixtures() {
        var expected = new java.util.HashSet<>(PACS_009_FIXTURES);
        expected.addAll(CAMT_056_FIXTURES);
        expected.addAll(CAMT_029_FIXTURES);
        return expected;
    }

    private String readFixture(String fileName) throws Exception {
        return Files.readString(FI_FIXTURES.resolve(fileName), StandardCharsets.UTF_8);
    }

    private void assertNoObviousRealOrSensitiveValues(String xml) {
        assertThat(xml).doesNotContain("John Doe", "Jane Doe", "1234567890123456");
        assertThat(PAN_LIKE_VALUE.matcher(xml).find()).isFalse();
        assertThat(REAL_CUSTOMER_LOOKING_NAME.matcher(xml).find()).isFalse();
    }

    private void assertUnsafeFixtureShape(String fixture, String xml) {
        if (fixture.endsWith("-unsafe.xml")) {
            assertThat(xml).contains("<!DOCTYPE", "<!ENTITY");
        } else {
            assertThat(xml).doesNotContain("<!DOCTYPE", "<!ENTITY");
        }
    }
}

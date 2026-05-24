package com.cib.payment.api.infrastructure.iso;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class IsoFixtureValidationTest {
    private static final Path ISO_FIXTURES = Path.of("src", "test", "resources", "iso");
    private static final String PAIN_001_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09";
    private static final String PAIN_002_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pain.002.001.10";
    private static final Set<String> PAIN_001_FIXTURES = Set.of(
            "pain001-success.xml",
            "pain001-rejection.xml",
            "pain001-suspicious.xml",
            "pain001-pending.xml",
            "pain001-timeout.xml",
            "pain001-invalid-missing-creditor.xml");
    private static final Map<String, String> PAIN_002_STATUSES = Map.of(
            "pain002-acsc.xml", "ACSC",
            "pain002-rjct.xml", "RJCT",
            "pain002-pdng.xml", "PDNG");
    private static final Pattern HKID = Pattern.compile("\\b[A-Z][0-9]{6}\\([0-9A]\\)\\b");
    private static final Pattern HK_PHONE_WITH_COUNTRY_CODE = Pattern.compile("\\+852\\s?\\d{8}\\b");
    private static final Pattern STANDALONE_HK_MOBILE = Pattern.compile("(?<![+\\d])[569]\\d{7}(?!\\d)");

    @Test
    void isoFixturesUseExpectedNamespacesAndSyntheticSafeValues() throws Exception {
        assertThat(Files.list(ISO_FIXTURES).map(path -> path.getFileName().toString()))
                .containsExactlyInAnyOrderElementsOf(allExpectedFixtures());

        for (var fixture : PAIN_001_FIXTURES) {
            var xml = readFixture(fixture);

            assertThat(xml).contains(PAIN_001_NAMESPACE);
            assertThat(xml).contains("HKD");
            assertThat(xml).contains("<EndToEndId>");
            assertNoObviousRealHongKongIdentifiers(xml);
        }

        for (var statusFixture : PAIN_002_STATUSES.entrySet()) {
            var xml = readFixture(statusFixture.getKey());

            assertThat(xml).contains(PAIN_002_NAMESPACE);
            assertThat(xml).contains("<TxSts>" + statusFixture.getValue() + "</TxSts>");
            assertNoObviousRealHongKongIdentifiers(xml);
        }
    }

    private Set<String> allExpectedFixtures() {
        var expected = new java.util.HashSet<>(PAIN_001_FIXTURES);
        expected.addAll(PAIN_002_STATUSES.keySet());
        return expected;
    }

    private String readFixture(String fileName) throws Exception {
        return Files.readString(ISO_FIXTURES.resolve(fileName), StandardCharsets.UTF_8);
    }

    private void assertNoObviousRealHongKongIdentifiers(String xml) {
        assertThat(HKID.matcher(xml).find()).isFalse();
        assertThat(HK_PHONE_WITH_COUNTRY_CODE.matcher(xml).find()).isFalse();
        assertThat(STANDALONE_HK_MOBILE.matcher(xml).find()).isFalse();
    }
}

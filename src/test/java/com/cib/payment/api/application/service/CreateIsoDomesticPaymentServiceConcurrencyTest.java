package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.application.port.PaymentEngineInitiationPort;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.EnginePaymentRecord;
import com.cib.payment.api.domain.model.InternalInterbankTransfer;
import com.cib.payment.api.domain.model.IsoPaymentCandidate;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.cib.payment.api.infrastructure.iso.Pain001Parser;
import com.cib.payment.api.infrastructure.persistence.InMemoryIdempotencyRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CreateIsoDomesticPaymentServiceConcurrencyTest {
    private final CountingPaymentEngine paymentEngine = new CountingPaymentEngine();
    private final IdempotencyRepository idempotencyRepository = new InMemoryIdempotencyRepository();
    private final CreateIsoDomesticPaymentService service = new CreateIsoDomesticPaymentService(
            new IsoPaymentAdmissionService(new Pain001Parser()),
            new RequestFingerprintService(),
            idempotencyRepository,
            paymentEngine,
            PaymentObservability.noop());

    @Test
    void concurrentEquivalentRequestsWithSameIdempotencyKeyInvokeEngineOnce() throws Exception {
        var callers = 16;
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(callers);
        var tasks = new ArrayList<Callable<String>>();
        for (int i = 0; i < callers; i++) {
            tasks.add(() -> {
                start.await(5, TimeUnit.SECONDS);
                return service.create(
                        readFixture("pain001-success.xml").replaceAll(">\\s+<", "><"),
                        "application/xml",
                        authorizationContext(),
                        "same-key",
                        "success");
            });
        }

        try {
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();

            var responses = new ArrayList<String>();
            for (var future : futures) {
                responses.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(responses).containsOnly(responses.getFirst());
            assertThat(paymentEngine.invocations()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private String readFixture(String fileName) throws Exception {
        return Files.readString(Path.of("src", "test", "resources", "iso", fileName), StandardCharsets.UTF_8);
    }

    private AuthorizationContext authorizationContext() {
        return new AuthorizationContext(
                "client-a",
                "client-a",
                Set.of("payments:create"),
                null,
                java.util.Map.of(),
                Instant.parse("2026-05-24T00:00:00Z"),
                "jwt-id",
                new CorrelationId("corr-concurrent"));
    }

    private static class CountingPaymentEngine implements PaymentEngineInitiationPort {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public EnginePaymentRecord initiate(
                IsoPaymentCandidate candidate,
                AuthorizationContext authorizationContext,
                CorrelationId correlationId,
                String idempotencyReference,
                String scenarioContext) {
            var count = invocations.incrementAndGet();
            sleep();
            var now = Instant.parse("2026-05-24T01:00:00Z");
            return new EnginePaymentRecord(
                    new PaymentId(UUID.nameUUIDFromBytes(("payment-" + count).getBytes(StandardCharsets.UTF_8))),
                    authorizationContext.clientId(),
                    candidate,
                    PaymentStatus.COMPLETED,
                    now,
                    now,
                    correlationId,
                    Optional.<InternalInterbankTransfer>empty(),
                    Optional.of("<Document><TxSts>ACSC</TxSts><count>" + count + "</count></Document>"),
                    Optional.<PaymentReason>empty(),
                    idempotencyReference);
        }

        int invocations() {
            return invocations.get();
        }

        private void sleep() {
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

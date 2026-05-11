package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.CreateDomesticPaymentRequest;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.api.dto.PaymentResponse;
import com.cib.payment.api.application.port.DownstreamPaymentOutcome;
import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.port.PaymentStatusRepository;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentRecord;
import com.cib.payment.api.domain.model.PaymentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CreateDomesticPaymentServiceConcurrencyTest {
    private static final int CONCURRENT_REQUESTS = 8;

    @Test
    void concurrentDuplicateRequestsCreateOnePaymentRecordAndOneIdempotencyRecord() throws Exception {
        var paymentRepository = new RecordingPaymentStatusRepository();
        var idempotencyRepository = new BarrierIdempotencyRepository(CONCURRENT_REQUESTS);
        var service = new CreateDomesticPaymentService(
                paymentRepository,
                idempotencyRepository,
                new RequestFingerprintService(),
                (instruction, authorizationContext, correlationId, mockScenario) ->
                        new DownstreamPaymentOutcome(PaymentStatus.COMPLETED, Optional.empty()),
                PaymentObservability.noop(),
                "MYR",
                Clock.fixed(Instant.parse("2026-05-09T00:00:00Z"), ZoneOffset.UTC));

        var responses = runConcurrently(() -> service.create(
                validRequest(),
                authorizationContext(),
                "duplicate-key",
                "success"));

        assertThat(responses)
                .extracting(PaymentResponse::paymentId)
                .containsOnly(responses.getFirst().paymentId());
        assertThat(paymentRepository.saveCount()).isEqualTo(1);
        assertThat(idempotencyRepository.recordCount()).isEqualTo(1);
    }

    private static ArrayList<PaymentResponse> runConcurrently(ConcurrentRequest request) throws Exception {
        var executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<java.util.concurrent.Future<PaymentResponse>>();
        try {
            for (var i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }

            start.countDown();

            var responses = new ArrayList<PaymentResponse>();
            for (var future : futures) {
                responses.add(future.get(5, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    private static AuthorizationContext authorizationContext() {
        return new AuthorizationContext(
                "client-a",
                "client-a",
                Set.of("payments:create"),
                null,
                Map.of(),
                Instant.parse("2026-05-09T00:00:00Z"),
                "jwt-1",
                new CorrelationId("corr-concurrent"));
    }

    private static CreateDomesticPaymentRequest validRequest() {
        return new CreateDomesticPaymentRequest(
                new AccountReferenceRequest("CIBBMYKL", "1234567890", "Acme Treasury"),
                new AccountReferenceRequest("PAYBMYKL", "9876543210", "Supplier Sdn Bhd"),
                new MoneyRequest("MYR", "1250.50"),
                "INV-2026-0001",
                "Invoice payment",
                null);
    }

    @FunctionalInterface
    private interface ConcurrentRequest {
        PaymentResponse call() throws Exception;
    }

    private static final class BarrierIdempotencyRepository implements IdempotencyRepository {
        private final ConcurrentHashMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();
        private final CyclicBarrier barrier;

        private BarrierIdempotencyRepository(int parties) {
            this.barrier = new CyclicBarrier(parties);
        }

        @Override
        public Optional<IdempotencyRecord> find(String clientId, String idempotencyKey) {
            return Optional.ofNullable(records.get(key(clientId, idempotencyKey)));
        }

        @Override
        public IdempotencyRecord saveIfAbsent(IdempotencyRecord record) {
            awaitBarrier();
            var existing = records.putIfAbsent(key(record.clientId(), record.idempotencyKey()), record);
            return existing == null ? record : existing;
        }

        private int recordCount() {
            return records.size();
        }

        private void awaitBarrier() {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException("Timed out waiting for concurrent duplicate requests", exception);
            }
        }

        private String key(String clientId, String idempotencyKey) {
            return clientId + ":" + idempotencyKey;
        }
    }

    private static final class RecordingPaymentStatusRepository implements PaymentStatusRepository {
        private final ConcurrentHashMap<PaymentId, PaymentRecord> records = new ConcurrentHashMap<>();
        private final AtomicInteger saveCount = new AtomicInteger();

        @Override
        public PaymentRecord save(PaymentRecord record) {
            saveCount.incrementAndGet();
            records.put(record.paymentId(), record);
            return record;
        }

        @Override
        public Optional<PaymentRecord> findByPaymentIdAndClientId(PaymentId paymentId, String clientId) {
            return Optional.ofNullable(records.get(paymentId))
                    .filter(record -> record.clientId().equals(clientId));
        }

        @Override
        public PaymentRecord updateStatus(PaymentId paymentId, PaymentStatus status, PaymentReason reason) {
            return records.compute(paymentId, (ignored, existing) -> new PaymentRecord(
                    existing.paymentId(),
                    existing.clientId(),
                    existing.instruction(),
                    status,
                    existing.createdAt(),
                    Instant.parse("2026-05-09T00:00:01Z"),
                    existing.correlationId(),
                    Optional.ofNullable(reason)));
        }

        private int saveCount() {
            return saveCount.get();
        }
    }
}

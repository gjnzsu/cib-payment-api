package com.cib.payment.api.infrastructure.engine;

import com.cib.payment.api.application.port.PaymentEngineRecordRepository;
import com.cib.payment.api.domain.model.EnginePaymentRecord;
import com.cib.payment.api.domain.model.PaymentId;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryPaymentEngineRecordRepository implements PaymentEngineRecordRepository {
    private final ConcurrentMap<PaymentId, EnginePaymentRecord> records = new ConcurrentHashMap<>();

    @Override
    public EnginePaymentRecord save(EnginePaymentRecord record) {
        records.put(record.paymentId(), record);
        return record;
    }

    @Override
    public Optional<EnginePaymentRecord> findByPaymentIdAndClientId(PaymentId paymentId, String clientId) {
        return Optional.ofNullable(records.get(paymentId))
                .filter(record -> record.clientId().equals(clientId));
    }
}

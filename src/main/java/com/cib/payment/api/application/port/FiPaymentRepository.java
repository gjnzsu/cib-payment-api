package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.FiPaymentId;
import com.cib.payment.api.domain.model.FiPaymentRecord;
import java.util.Optional;

public interface FiPaymentRepository {
    Optional<FiPaymentRecord> findById(FiPaymentId paymentId);

    Optional<FiPaymentRecord> findByIdAndOwnerClientId(FiPaymentId paymentId, String ownerClientId);

    FiPaymentRecord save(FiPaymentRecord record);
}

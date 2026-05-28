package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.FiPaymentId;
import com.cib.payment.api.domain.model.RecallInvestigationRecord;
import java.util.Optional;

public interface RecallInvestigationRepository {
    Optional<RecallInvestigationRecord> findByPaymentId(FiPaymentId paymentId);

    Optional<RecallInvestigationRecord> findByPaymentIdAndOwnerClientId(FiPaymentId paymentId, String ownerClientId);

    RecallInvestigationRecord saveIfAbsent(RecallInvestigationRecord record);
}

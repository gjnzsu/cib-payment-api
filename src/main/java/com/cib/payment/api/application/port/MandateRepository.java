package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.MandateId;
import com.cib.payment.api.domain.model.MandateRecord;
import java.util.Optional;

public interface MandateRepository {
    MandateRecord save(MandateRecord record);

    Optional<MandateRecord> find(MandateId mandateId);

    Optional<MandateRecord> findByClientIdAndMandateReference(String clientId, String mandateReference);
}

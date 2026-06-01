package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.RtgsPaymentId;
import com.cib.payment.api.domain.model.RtgsPaymentRecord;
import java.util.Optional;

public interface RtgsPaymentRepository {
    RtgsPaymentRecord save(RtgsPaymentRecord record);

    Optional<RtgsPaymentRecord> find(RtgsPaymentId paymentId);
}

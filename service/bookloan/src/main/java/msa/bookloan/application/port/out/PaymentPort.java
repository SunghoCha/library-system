package msa.bookloan.application.port.out;

import msa.bookloan.application.port.out.response.PaymentResult;

public interface PaymentPort {
    PaymentResult charge(String sagaId, Long memberId, Long amount);
    void refund(String sagaId, Long paymentId);
}

package msa.bookloan.application.port.out;

public interface PaymentPort {
    PaymentResult charge(String sagaId, Long memberId, Long amount);
    void refund(String sagaId, Long paymentId);
}

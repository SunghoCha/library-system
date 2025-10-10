package msa.bookloan.application.port.out.response;

public record PaymentResult(
        boolean success,
        Long paymentId
) {
}

package msa.bookloan.adapter.out.client;

import lombok.RequiredArgsConstructor;
import msa.bookloan.application.port.out.PaymentPort;
import msa.bookloan.application.port.out.response.PaymentResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentAdaptor implements PaymentPort {

    @Override
    public PaymentResult charge(String sagaId, Long memberId, Long amount) {
        return null;
    }

    @Override
    public void refund(String sagaId, Long paymentId) {

    }
}

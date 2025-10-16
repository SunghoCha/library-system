package msa.bookloan.application.saga.exception;

import msa.bookloan.application.saga.exception.errorCode.SagaErrorCode;
import msa.common.exception.BusinessException;

import java.util.Map;

public class SagaNotFoundException extends BusinessException {
    public SagaNotFoundException(String sagaId) {
        super(SagaErrorCode.SAGA_NOT_FOUND, Map.of("sagaId", sagaId));
    }
}

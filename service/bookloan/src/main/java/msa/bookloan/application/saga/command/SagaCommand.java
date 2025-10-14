package msa.bookloan.application.saga.command;

public interface SagaCommand {
    String sagaId();
    Long commandId();
}

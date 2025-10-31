package msa.common.events.bookloan.saga.command;

public interface SagaCommand {
    Long sagaId();
    Long commandId();
    String type();
}

package msa.common.events.bookloan.saga.command;

public interface SagaCommand {
    String sagaId();
    Long commandId();
    String type();
}

package msa.common.events;

public interface DomainEvent {

    long getAggregateId();
    String getAggregateType();
    long getAggregateVersion();

}

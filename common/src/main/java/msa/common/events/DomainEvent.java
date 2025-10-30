package msa.common.events;

@Deprecated
public interface DomainEvent {

    long getAggregateId();
    String getAggregateType();
    long getAggregateVersion();

}

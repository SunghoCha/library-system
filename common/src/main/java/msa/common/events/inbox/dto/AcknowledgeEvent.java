package msa.common.events.inbox.dto;


import org.springframework.kafka.support.Acknowledgment;

public class AcknowledgeEvent {
    private final Acknowledgment ack;

    public AcknowledgeEvent(Acknowledgment ack) {
        this.ack = ack;
    }

    public Acknowledgment getAck() {
        return ack;
    }
}

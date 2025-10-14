package msa.common.events.inbox.record;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;
import msa.common.domain.base.BaseTimeEntity;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class InboxDeadLetter extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "partition_no", nullable = false)
    private Integer partitionNo;

    @Column(name = "record_offset", nullable = false)
    private Long recordOffset;

    @Column(name = "payload_json", columnDefinition = "json")
    private String payloadJson;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "error_category", nullable = false, length = 64)
    private String errorCategory;
}

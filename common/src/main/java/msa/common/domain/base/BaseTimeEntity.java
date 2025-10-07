package msa.common.domain.base;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@SuperBuilder
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseTimeEntity {

    @CreatedDate @Column(name = "created_at", updatable = false, columnDefinition = "datetime(6)")
    private LocalDateTime createdAt;

    @LastModifiedDate @Column(name = "updated_at", columnDefinition = "datetime(6)")
    private LocalDateTime updatedAt;
}

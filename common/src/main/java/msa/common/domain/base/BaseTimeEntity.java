package msa.common.domain.base;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdTime;

    @LastModifiedDate
    private LocalDateTime updateTime;

    // JdbcTemplate 사용 시 수동으로 값을 설정할 수 있도록 setter 추가
    protected void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    protected void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}

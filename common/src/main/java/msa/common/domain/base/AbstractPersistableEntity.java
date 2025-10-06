package msa.common.domain.base;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.domain.Persistable;

import static lombok.AccessLevel.PROTECTED;

//@SuperBuilder
@MappedSuperclass
@NoArgsConstructor(access = PROTECTED)
public abstract class AbstractPersistableEntity extends BaseTimeEntity implements Persistable<Long> {

//    @Id
//    @Column(name = "id", nullable = false)
//    protected Long id;
//
//    @Transient
//    @Builder.Default
//    private boolean isNew = true;
//
//    @Override
//    public boolean isNew() {
//        return isNew;
//    }
//
//    @Override
//    public Long getId() {
//        return id;
//    }
//
//    @PostPersist
//    @PostLoad
//    void markNotNew() {
//        this.isNew = false;
//    }
}

package msa.bookcatalog.domain.model;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import msa.bookcatalog.repository.BookCatalogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.jpa.repository.support.JpaMetamodelEntityInformation;
import org.springframework.data.jpa.repository.support.JpaPersistableEntityInformation;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "app.aladin.enabled=false",
        "app.scheduling.enabled=false"
})
public class JpaTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private BookCatalogRepository repository;

    private BookCatalog base(Long id) {
        return BookCatalog.builder()
                .id(id)
                .isbn13("9781234567890")
                .title("T")
                .author("A")
                .publisher("P")
                .coverImageUrl("U")
                .description("D")
                .publishDate(LocalDate.of(2024, 1, 1))
                .category(BookCategory.NOVEL)
                .bookType(BookType.STANDARD)
                .build();
    }

//    @Test
//    @DisplayName("실행 시 선택되는 EntityInformation 구현체를 확인한다 (Persistable 구현 → JpaPersistableEntityInformation)")
//    void whichEntityInformation() {
//
//        JpaEntityInformation<BookCatalog, ?> info = JpaEntityInformationSupport.getEntityInformation(BookCatalog.class, em);
//
//        System.out.println("== EntityInformation impl = " + info.getClass().getName());
//        // Persistable<T> 구현 엔티티이므로 JpaPersistableEntityInformation가 선택된다.
//        assertThat(info)
//                .isInstanceOfAny(JpaPersistableEntityInformation.class, JpaMetamodelEntityInformation.class);
//    }
//
//    @Test
//    @DisplayName("Persistable.isNew()가 true이면, ID가 있어도 isNew=true로 판정한다")
//    void isNewTrueWhenPersistableSaysNew() {
//        BookCatalog entity = BookCatalog.builder()
//                //.id(123L)                               // 스노우플레이크 선할당 가정
//                .title("t")
//                .isbn13("1234567890123")
//                .category(BookCategory.values()[0])     // 사용 중인 임의의 카테고리 상수
//                .bookType(BookType.values()[0])         // 사용 중인 임의의 북타입 상수
//                .build();
//        //entity.setVersion(0L);
//
//        JpaEntityInformation<BookCatalog, ?> info = JpaEntityInformationSupport.getEntityInformation(BookCatalog.class, em);
//        System.out.println("== EntityInformation impl = " + info.getClass().getName());
//
//        boolean isNew = info.isNew(entity);
//        assertThat(isNew).isTrue();                     // → persist 경로
//    }
//
//    @Test
//    @DisplayName("save()가 persist 경로일 때, 반환 인스턴스는 전달한 객체와 동일하다")
//    void saveReturnsSameInstanceWhenPersist() {
//        BookCatalog entity = BookCatalog.builder()
//                .id(999L)
//                .title("t")
//                .isbn13("1234567890123")
//                .category(BookCategory.values()[0])
//                .bookType(BookType.values()[0])
//                .build();
//
//        //entity.setVersion(0L);
//
//        BookCatalog saved = repository.save(entity);
//        assertThat(saved).isSameAs(entity);             // persist면 same, merge면 not same
//    }
//
//    @Test
//    @DisplayName("isNew=true이면 save가 INSERT를 타고, @PostPersist 이후 isNew=false로 바뀐다")
//    void insert_then_isNew_becomes_false() {
//        BookCatalog c = base(100L);
//        // 빌더로 만든 직후엔 true여야 한다
//        assertThat(c.isNew()).isTrue();
//
//        repository.saveAndFlush(c); // INSERT
//        assertThat(c.isNew()).isFalse(); // @PostPersist로 false
//    }
//
//    @Test // PostLoad 필요한 이유
//    @DisplayName("DB에서 로드된 엔티티는 @PostLoad로 isNew=false → save가 UPDATE 경로")
//    void load_marks_not_new_then_update() {
//        repository.saveAndFlush(base(101L));
//        em.clear();
//
//        BookCatalog loaded = repository.findById(101L).orElseThrow();
//        assertThat(loaded.isNew()).isFalse(); // @PostLoad 적용 확인
//
//        // 변경 후 저장 -> UPDATE
//        loaded.edit(loaded.toEditorBuilder().title("T2").build());
//        repository.saveAndFlush(loaded);
//
//        em.clear();
//        BookCatalog after = repository.findById(101L).orElseThrow();
//        assertThat(after.getTitle()).isEqualTo("T2");
//    }
//
//    @Test
//    @DisplayName("같은 영속성 컨텍스트에서 같은 인스턴스를 재-save해도 INSERT 재시도하지 않는다")
//    void same_instance_resave_is_update_or_noop() {
//        BookCatalog c = base(102L);
//        repository.saveAndFlush(c); // INSERT
//        // isNew는 이미 false
//        assertThat(c.isNew()).isFalse();
//
//        // 다시 저장해도 INSERT 아님(예외 없이 통과하면 충분)
//        repository.saveAndFlush(c);
//
//        Optional<BookCatalog> loaded = repository.findById(102L);
//        assertThat(loaded).isPresent();
//    }
//
//    @Test
//    @DisplayName("DB에 이미 있는 id로 새 인스턴스를 재조립해서 save하면 persist 시도 → 예외")
//    void detached_rebuild_without_load_then_save_throws() {
//        repository.saveAndFlush(base(103L));
//        em.clear();
//
//        // 같은 id로 '새 인스턴스'를 빌더로 재조립(로딩 없음 → isNew=true 유지)
//        BookCatalog detached = base(103L);
//        assertThat(detached.isNew()).isTrue();
//
//        // persist 경로를 타므로 PK 충돌 계열 예외 발생(JPA 구현에 따라 다를 수 있어 둘 다 허용)
//        assertThatThrownBy(() -> repository.saveAndFlush(detached))
//                .isInstanceOfAny(EntityExistsException.class,
//                        DataIntegrityViolationException.class);
//    }

}

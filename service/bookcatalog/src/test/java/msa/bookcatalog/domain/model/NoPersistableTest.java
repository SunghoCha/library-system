package msa.bookcatalog.domain.model;

import jakarta.persistence.EntityManager;
import msa.bookcatalog.repository.BookCatalogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.jpa.repository.support.JpaMetamodelEntityInformation;
import org.springframework.data.jpa.repository.support.JpaPersistableEntityInformation;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "app.aladin.enabled=false",
        "app.scheduling.enabled=false"
})
public class NoPersistableTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private BookCatalogRepository repository;

//    @Test
//    @DisplayName("실행 시 선택되는 EntityInformation 구현체를 확인한다 (Persistable 제거 버전)")
//    void whichEntityInformation() {
//
//        JpaEntityInformation<BookCatalog, ?> info =
//                JpaEntityInformationSupport.getEntityInformation(BookCatalog.class, em);
//
//        System.out.println("== EntityInformation impl = " + info.getClass().getName());
//        assertThat(info).isInstanceOf(JpaMetamodelEntityInformation.class);
//    }
//
//    @Test
//    @DisplayName("@Version이 null이면, ID가 있어도 isNew=true로 판정한다 (Persistable 제거 버전)")
//    void isNewTrueWhenVersionNullEvenWithId() {
//        BookCatalog entity = BookCatalog.builder()
//                .id(123L)                               // 스노우플레이크 선할당 가정
//                .title("t")
//                .isbn13("1234567890123")
//                .category(BookCategory.values()[0])     // 사용 중인 임의의 카테고리 상수
//                .bookType(BookType.values()[0])         // 사용 중인 임의의 북타입 상수
//                .build();
//
//        JpaEntityInformation<BookCatalog, ?> info =
//                JpaEntityInformationSupport.getEntityInformation(BookCatalog.class, em);
//
//        boolean isNew = info.isNew(entity);
//        assertThat(isNew).isTrue(); // → persist 경로
//    }
//
//    @Test
//    @DisplayName("save()가 persist 경로일 때, 반환 인스턴스는 전달한 객체와 동일하다 (Persistable 제거 버전)")
//    void saveReturnsSameInstanceWhenPersist() {
//        BookCatalog entity = BookCatalog.builder()
//                .id(123L)                               // 스노우플레이크 선할당 가정
//                .title("t")
//                .isbn13("1234567890123")
//                .category(BookCategory.values()[0])     // 사용 중인 임의의 카테고리 상수
//                .bookType(BookType.values()[0])         // 사용 중인 임의의 북타입 상수
//                .build();
//        // @Version Long version = null 상태 → persist
//
//        BookCatalog saved = repository.save(entity);
//        assertThat(saved).isSameAs(entity); // persist면 same, merge면 not same
//    }
}

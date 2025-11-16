package msa.inventory.domain.model;

import jakarta.persistence.EntityManager;
import msa.inventory.adaptor.out.persistence.bookcopy.repository.BookCopyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class JpaTest {

    @Autowired
    private BookCopyRepository repository;

    @Autowired
    private EntityManager em;

//    @Test
//    @DisplayName("실행 시 선택되는 EntityInformation 구현체를 확인한다")
//    void whichEntityInformation() {
//
//        JpaEntityInformation<BookCopy, ?> info = JpaEntityInformationSupport.getEntityInformation(BookCopy.class, em);
//
//        System.out.println("== EntityInformation impl = " + info.getClass().getName());
//        assertThat(info).isInstanceOf(JpaMetamodelEntityInformation.class);
//    }
//
//    @Test
//    void isNewTrueWhenVersionNullEvenWithId() {
//        BookCopy entity = BookCopy.builder()
//                .id(999L)
//                .status(CopyStatus.AVAILABLE)
//                .build();
//        //entity.setVersion(0L);
//
//        JpaEntityInformation<BookCopy, ?> info = JpaEntityInformationSupport.getEntityInformation(BookCopy.class, em);
//        System.out.println("== EntityInformation impl = " + info.getClass().getName());
//
//        boolean isNew = info.isNew(entity);
//        assertThat(isNew).isTrue();      // → persist 경로
//
//        assertThat(info).isInstanceOf(JpaMetamodelEntityInformation.class);
//    }
//
//    @Test
//    void saveReturnsSameInstanceWhenPersist() {
//        BookCopy entity = BookCopy.builder()
//                //.id(999L)
//                .status(CopyStatus.AVAILABLE)
//                .build();
//        //entity.setVersion(0L);
//
//        BookCopy saved = repository.save(entity);
//        assertThat(saved).isSameAs(entity);      // persist면 same, merge면 not same
//    }

}
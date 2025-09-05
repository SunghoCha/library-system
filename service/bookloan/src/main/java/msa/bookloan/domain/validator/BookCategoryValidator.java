package msa.bookloan.domain.validator;

import msa.common.events.bookcatalog.BookCatalogChangedEvent;
import org.springframework.stereotype.Component;

//@Component
//public class BookCategoryValidator {
//
//    public void validate(BookCatalogChangedEvent event) {
//        if (event.getCategoryId() == null) {
//            throw new IllegalArgumentException("categoryId는 필수입니다.");
//        }
//        if (event.getName().isBlank()) {
//            throw new IllegalArgumentException("name은 비워둘 수 없습니다.");
//        }
//        // 추가 도메인 룰 검증…
//    }
//}

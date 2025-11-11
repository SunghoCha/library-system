package msa.bookloan.testsupport;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class DatabaseCleaner {

    @PersistenceContext
    private EntityManager entityManager;

    private List<String> tableNames;

    @PostConstruct
    @SuppressWarnings("unchecked")
    private void findTableNames() {
        List<Object> tableInfos = entityManager.createNativeQuery("SHOW TABLES").getResultList();

        // TODO: Flyway 관련 테이블 생길땐 제외하기
        this.tableNames = new ArrayList<>();
        for (Object tableInfo : tableInfos) {
            String tableName = tableInfo.toString();
            this.tableNames.add(tableName);
        }
    }

    @Transactional
    public void clear() {
        // 영속성 컨텍스트 비우기
        entityManager.clear();

        // FK 제약 조건 비활성화 (MySQL)
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();

        // 저장해둔 모든 테이블 TRUNCATE
        for (String tableName : tableNames) {
            entityManager.createNativeQuery("TRUNCATE TABLE " + tableName).executeUpdate();
        }

        // FK 제약 조건 다시 활성화
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }
}

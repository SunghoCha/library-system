package msa.bookcatalog.config;

import com.querydsl.jpa.JPQLTemplates;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryDslConfig {

    @PersistenceContext
    private EntityManager em;

    @Bean // Qdsl에서 groupby, orderby와 관련한 오류 있음. 일단 버전 4.x로 낮추거나 JPQLTemplates.DEFAULT 설정으로 해걸해야함
    public JPAQueryFactory jpaQueryFactory(){
        return new JPAQueryFactory(JPQLTemplates.DEFAULT ,em);
    }
}

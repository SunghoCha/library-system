package msa.bookloan.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class MySqlIntegrationTestBase {

    protected static final MySQLContainer<?> MYSQL;

    static {
        MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.3.0"))
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
        MYSQL.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // MYSQL 설정
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);

    }
}

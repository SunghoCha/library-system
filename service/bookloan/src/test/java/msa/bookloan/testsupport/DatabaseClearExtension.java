package msa.bookloan.testsupport;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class DatabaseClearExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        DatabaseCleaner databaseCleaner = applicationContext.getBean(DatabaseCleaner.class);
        databaseCleaner.clear();
    }
}

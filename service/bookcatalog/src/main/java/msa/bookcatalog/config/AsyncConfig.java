package msa.bookcatalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean("DEFAULT_TASK_EXECUTOR")
    public ThreadPoolTaskExecutor defaultTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(10);
        ex.setMaxPoolSize(20);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("default-async-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(10);
        return ex;
    }

    @Bean("EVENT_ASYNC_TASK_EXECUTOR")
    public ThreadPoolTaskExecutor kafkaListenerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("kafka-listener-");
        executor.setAwaitTerminationSeconds(10);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return defaultTaskExecutor();
    }
}

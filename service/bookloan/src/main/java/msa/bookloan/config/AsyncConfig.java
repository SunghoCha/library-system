package msa.bookloan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

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
        return kafkaListenerTaskExecutor();
    }
}

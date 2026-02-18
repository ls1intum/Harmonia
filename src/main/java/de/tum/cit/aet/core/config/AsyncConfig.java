package de.tum.cit.aet.core.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration class for asynchronous task execution of the pair programming processing
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Creates and configures a dedicated {@link Executor} bean for handling
     * attendance-related asynchronous tasks.
     * @return A configured {@link Executor} for attendance task processing
     */
    @Bean(name = "attendanceTaskExecutor")
    public Executor attendanceTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("attendance-");
        executor.initialize();
        return executor;
    }
}

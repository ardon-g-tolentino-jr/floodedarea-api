package com.humanworkstream.baha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class BahaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BahaApiApplication.class, args);
    }

    /** Small bounded pool for @Async work (currently: social-media publishing). Kept tiny —
     *  this is best-effort background fan-out, not a request-serving pool. */
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("baha-async-");
        executor.initialize();
        return executor;
    }
}

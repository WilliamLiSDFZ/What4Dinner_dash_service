package today.what4dinner.what4dinner_dash_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class SpringConfig {

    /**
     * Pool for AI recipe generation. Deliberately small with a bounded queue: each task
     * uploads several images to the model, so it is slow and costly, and an unbounded queue
     * would let a burst pile up invisibly. {@code CallerRunsPolicy} pushes back on the
     * submitting request instead of silently dropping work once the queue is full.
     */
    @Bean("aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ai-gen-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Separate pool for dish-photo generation, deliberately not shared with
     * {@link #aiTaskExecutor()}. An image takes tens of seconds, so sharing would let photo
     * work starve recipe generation, and once the shared queue filled, {@code CallerRunsPolicy}
     * would run a minute-long image job on the HTTP request thread — turning the 202 back into
     * a blocking call, which is the exact bug that was fixed there.
     */
    @Bean("aiImageExecutor")
    public ThreadPoolTaskExecutor aiImageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("ai-img-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

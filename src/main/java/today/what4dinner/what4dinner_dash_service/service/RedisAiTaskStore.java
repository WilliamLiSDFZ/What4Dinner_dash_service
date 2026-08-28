package today.what4dinner.what4dinner_dash_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.AiTask;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed task state. Keys are {@code ai:task:{taskId}} holding the serialized
 * {@link AiTask}, with a TTL so finished tasks expire instead of accumulating.
 */
@Service
public class RedisAiTaskStore implements AiTaskStore {

    private static final Logger log = LoggerFactory.getLogger(RedisAiTaskStore.class);

    private static final String KEY_PREFIX = "ai:task:";

    /** Long enough for a client to poll a finished task, short enough not to accumulate. */
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisAiTaskStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public AiTask create(UUID taskId, UUID recipeId) {
        AiTask task = new AiTask(taskId, recipeId, "pending", null);
        write(task);
        return task;
    }

    @Override
    public void markProcessing(UUID taskId) {
        update(taskId, t -> t.setStatus("processing"));
    }

    @Override
    public void markDone(UUID taskId) {
        update(taskId, t -> t.setStatus("done"));
    }

    @Override
    public void markFailed(UUID taskId, String errorMessage) {
        update(taskId, t -> {
            t.setStatus("failed");
            t.setErrorMessage(errorMessage);
        });
    }

    @Override
    public Optional<AiTask> find(UUID taskId) {
        String json = read(KEY_PREFIX + taskId);
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(json, AiTask.class));
    }

    /**
     * Distinguishes "no such task" from "Redis is down". Without this the connection failure
     * surfaces as a 500 and an unknown task and an outage look identical to the client — the
     * same reason GCS and Anthropic degrade to 503 here rather than throwing.
     */
    private String read(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException e) {
            // Keep the cause: connection-refused, auth failure and timeout all surface as the
            // same 503, and only the cause distinguishes them.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Task storage is unavailable", e);
        }
    }

    /**
     * A status update on a task that has already expired is not worth failing the worker
     * for — the recipe row is the durable record of the outcome.
     */
    private void update(UUID taskId, java.util.function.Consumer<AiTask> mutation) {
        Optional<AiTask> existing;
        try {
            existing = find(taskId);
        } catch (RuntimeException e) {
            log.warn("Could not read AI task {} for status update: {}", taskId, e.toString());
            return;
        }
        if (existing.isEmpty()) {
            log.warn("AI task {} is gone from Redis; skipping status update", taskId);
            return;
        }
        AiTask task = existing.get();
        mutation.accept(task);
        write(task);
    }

    private void write(AiTask task) {
        try {
            redis.opsForValue().set(KEY_PREFIX + task.getTaskId(), objectMapper.writeValueAsString(task), TTL);
        } catch (DataAccessException e) {
            // Keep the cause: connection-refused, auth failure and timeout all surface as the
            // same 503, and only the cause distinguishes them.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Task storage is unavailable", e);
        }
    }
}

package today.what4dinner.what4dinner_dash_service.aspects;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

/**
 * Logs exceptions thrown out of the repository and service layers.
 *
 * <p>Without this, a {@link ResponseStatusException} is handled by Spring's
 * {@code ResponseStatusExceptionResolver}, which returns the status and logs nothing — so a
 * {@code 503} reaches the client with no trace of <em>why</em> on the server. That is exactly
 * the case this exists for.
 *
 * <p>Severity is split by status rather than by exception type, because this project signals
 * every error with {@code ResponseStatusException} and has no custom exception hierarchy:
 * <ul>
 *   <li><b>4xx</b> — the caller sent something wrong (blank title, unknown id, foreign object
 *       key). Routine and often repetitive, so DEBUG and no stack trace.</li>
 *   <li><b>5xx</b> — something on our side is broken or unreachable. ERROR with the full
 *       stack trace, including the underlying cause.</li>
 *   <li>anything else — unexpected, so ERROR with the stack trace.</li>
 * </ul>
 */
@Aspect
@Component
@Slf4j
public class LogAspect {

    @AfterThrowing(
            pointcut = "execution(* today.what4dinner.what4dinner_dash_service.repository.*.*(..))",
            throwing = "e")
    public void daoLogException(JoinPoint joinPoint, Exception e) {
        logFailure("repository", joinPoint, e);
    }

    @AfterThrowing(
            pointcut = "execution(* today.what4dinner.what4dinner_dash_service.service.*.*(..))",
            throwing = "e")
    public void serviceLogException(JoinPoint joinPoint, Exception e) {
        logFailure("service", joinPoint, e);
    }

    private void logFailure(String layer, JoinPoint joinPoint, Exception e) {
        String method = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        String args = Arrays.toString(joinPoint.getArgs());

        if (e instanceof ResponseStatusException rse) {
            if (rse.getStatusCode().is4xxClientError()) {
                log.debug("{} {} rejected {}: {} {}", layer, method, args,
                        rse.getStatusCode().value(), rse.getReason());
                return;
            }
            // 5xx: the reason is ours, but the cause is what actually explains it - log both.
            log.error("{} {} failed {} -> {} {}", layer, method, args,
                    rse.getStatusCode().value(), rse.getReason(), e);
            return;
        }
        log.error("{} {} threw with argument {}", layer, method, args, e);
    }
}

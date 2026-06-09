package com.uip.backend.bms.security;

import com.uip.backend.bms.api.RequiresConfirmation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * AOP aspect that enforces operator confirmation for BMS device commands.
 *
 * <p>Intercepts methods annotated with {@link RequiresConfirmation} and validates
 * confirmation headers before allowing execution.</p>
 *
 * <h3>Validation rules:</h3>
 * <ul>
 *   <li>All levels: {@code X-Confirmation-Reason} header must be present and ≥10 chars</li>
 *   <li>HIGH/EMERGENCY: {@code X-Confirmation-Actuator-Name} header must be present</li>
 * </ul>
 *
 * <p>All confirmed commands are audit-logged.</p>
 */
@Aspect
@Component
@Slf4j
public class CommandConfirmationAspect {

    private static final int MIN_REASON_LENGTH = 10;

    private static final String HEADER_REASON = "X-Confirmation-Reason";
    private static final String HEADER_ACTUATOR_NAME = "X-Confirmation-Actuator-Name";

    @Around("@annotation(requiresConfirmation)")
    public Object enforceConfirmation(ProceedingJoinPoint joinPoint,
                                      RequiresConfirmation requiresConfirmation) throws Throwable {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            // Non-HTTP context (e.g., scheduled task) — skip confirmation
            return joinPoint.proceed();
        }

        HttpServletRequest request = attrs.getRequest();
        RequiresConfirmation.DangerLevel level = requiresConfirmation.dangerLevel();

        // 1. Validate reason header present
        String reason = request.getHeader(HEADER_REASON);
        if (reason == null || reason.isBlank()) {
            log.warn("[CMD-SAFETY] Rejected: missing {} for level={}", HEADER_REASON, level);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirmation required: provide " + HEADER_REASON + " header");
        }

        // 2. Validate reason minimum length
        if (reason.length() < MIN_REASON_LENGTH) {
            log.warn("[CMD-SAFETY] Rejected: reason too short ({} chars) for level={}",
                    reason.length(), level);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirmation reason must be at least " + MIN_REASON_LENGTH + " characters");
        }

        // 3. HIGH/EMERGENCY: validate actuator name match
        if (level == RequiresConfirmation.DangerLevel.HIGH
                || level == RequiresConfirmation.DangerLevel.EMERGENCY) {
            String actuatorName = request.getHeader(HEADER_ACTUATOR_NAME);
            if (actuatorName == null || actuatorName.isBlank()) {
                log.warn("[CMD-SAFETY] Rejected: missing {} for level={}", HEADER_ACTUATOR_NAME, level);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        level + " commands require " + HEADER_ACTUATOR_NAME + " header");
            }
        }

        // 4. Audit trail
        String user = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
        String actuator = request.getHeader(HEADER_ACTUATOR_NAME);
        log.info("[CMD-SAFETY] Confirmed: level={} user='{}' reason='{}' actuator='{}' method={}",
                level, user, reason, actuator, joinPoint.getSignature().toShortString());

        return joinPoint.proceed();
    }
}

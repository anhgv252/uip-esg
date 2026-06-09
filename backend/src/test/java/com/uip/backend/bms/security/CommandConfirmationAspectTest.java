package com.uip.backend.bms.security;

import com.uip.backend.bms.api.RequiresConfirmation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Command Confirmation Aspect — Safety validation")
class CommandConfirmationAspectTest {

    private CommandConfirmationAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new CommandConfirmationAspect();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void setRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // ── Missing reason ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing confirmation reason → 400 BAD_REQUEST")
    void missingReason_throws400() {
        setRequest(new MockHttpServletRequest());

        assertThatThrownBy(() -> aspect.enforceConfirmation(
                Mockito.mock(ProceedingJoinPoint.class),
                stubAnnotation(RequiresConfirmation.DangerLevel.LOW)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    // ── Reason too short ────────────────────────────────────────────────────

    @Test
    @DisplayName("Reason < 10 chars → 400 BAD_REQUEST")
    void shortReason_throws400() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Confirmation-Reason", "short");
        setRequest(req);

        assertThatThrownBy(() -> aspect.enforceConfirmation(
                Mockito.mock(ProceedingJoinPoint.class),
                stubAnnotation(RequiresConfirmation.DangerLevel.LOW)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at least 10 characters");
    }

    // ── EMERGENCY without actuator name ─────────────────────────────────────

    @Test
    @DisplayName("EMERGENCY without actuator name → 400 BAD_REQUEST")
    void emergencyWithoutName_throws400() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Confirmation-Reason", "Emergency shutdown needed now");
        setRequest(req);

        assertThatThrownBy(() -> aspect.enforceConfirmation(
                Mockito.mock(ProceedingJoinPoint.class),
                stubAnnotation(RequiresConfirmation.DangerLevel.EMERGENCY)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("X-Confirmation-Actuator-Name");
    }

    // ── HIGH without actuator name ──────────────────────────────────────────

    @Test
    @DisplayName("HIGH without actuator name → 400 BAD_REQUEST")
    void highWithoutName_throws400() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Confirmation-Reason", "Critical HVAC shutdown");
        setRequest(req);

        assertThatThrownBy(() -> aspect.enforceConfirmation(
                Mockito.mock(ProceedingJoinPoint.class),
                stubAnnotation(RequiresConfirmation.DangerLevel.HIGH)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("X-Confirmation-Actuator-Name");
    }

    private ProceedingJoinPoint mockJoinPoint() throws Throwable {
        ProceedingJoinPoint jp = Mockito.mock(ProceedingJoinPoint.class);
        Mockito.when(jp.proceed()).thenReturn("ok");
        var signature = Mockito.mock(org.aspectj.lang.Signature.class);
        Mockito.when(signature.toShortString()).thenReturn("BmsDeviceCommand.sendCommand(..)");
        Mockito.when(jp.getSignature()).thenReturn(signature);
        return jp;
    }

    // ── Valid LOW confirmation ──────────────────────────────────────────────

    @Test
    @DisplayName("Valid LOW confirmation → proceeds without exception")
    void validLowConfirmation_proceeds() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Confirmation-Reason", "Routine maintenance check");
        setRequest(req);

        ProceedingJoinPoint jp = mockJoinPoint();

        Object result = aspect.enforceConfirmation(jp, stubAnnotation(RequiresConfirmation.DangerLevel.LOW));

        assertThat(result).isEqualTo("ok");
        Mockito.verify(jp).proceed();
    }

    // ── Valid EMERGENCY confirmation ────────────────────────────────────────

    @Test
    @DisplayName("Valid EMERGENCY confirmation with actuator name → proceeds")
    void validEmergencyConfirmation_proceeds() throws Throwable {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Confirmation-Reason", "Fire suppression system activation");
        req.addHeader("X-Confirmation-Actuator-Name", "FIRE_SUPPRESS_01");
        setRequest(req);

        ProceedingJoinPoint jp = mockJoinPoint();

        Object result = aspect.enforceConfirmation(jp, stubAnnotation(RequiresConfirmation.DangerLevel.EMERGENCY));

        assertThat(result).isEqualTo("ok");
        Mockito.verify(jp).proceed();
    }

    // ── No HTTP context (non-web) ───────────────────────────────────────────

    @Test
    @DisplayName("Non-HTTP context (scheduled task) → proceeds without confirmation")
    void nonHttpContext_proceeds() throws Throwable {
        RequestContextHolder.resetRequestAttributes();

        ProceedingJoinPoint jp = mockJoinPoint();

        Object result = aspect.enforceConfirmation(jp, stubAnnotation(RequiresConfirmation.DangerLevel.EMERGENCY));

        assertThat(result).isEqualTo("ok");
        Mockito.verify(jp).proceed();
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private RequiresConfirmation stubAnnotation(RequiresConfirmation.DangerLevel level) {
        return new RequiresConfirmation() {
            @Override
            public DangerLevel dangerLevel() { return level; }
            @Override
            public String reason() { return ""; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RequiresConfirmation.class;
            }
        };
    }
}

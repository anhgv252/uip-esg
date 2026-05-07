package com.uip.backend.notification.push;

import com.uip.backend.notification.service.AlertNotification;
import com.uip.backend.notification.service.NotificationChannel;
import com.uip.backend.notification.service.NotificationRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationRouter.
 *
 * Routes AlertNotification to all registered NotificationChannel implementations.
 * Each channel is isolated -- failure in one does not block others.
 *
 * Sprint 5 push notification backend spike.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationRouter")
class NotificationRouterTest {

    @Mock private NotificationChannel sseChannel;
    @Mock private NotificationChannel pushChannel;

    private NotificationRouter notificationRouter;

    private AlertNotification buildAlert(String tenantId, String severity) {
        return new AlertNotification(
                "SENSOR-AIR-001", "environment", severity,
                "AQI reached threshold", tenantId, 1L);
    }

    @BeforeEach
    void setUp() {
        lenient().when(sseChannel.getChannelName()).thenReturn("sse");
        lenient().when(pushChannel.getChannelName()).thenReturn("web-push");
    }

    // ========================================================================
    // Route to all channels
    // ========================================================================

    @Nested
    @DisplayName("Channel Routing")
    class ChannelRouting {

        @Test
        @DisplayName("Alert is dispatched to all registered channels")
        void alert_dispatchedToAllChannels() {
            notificationRouter = new NotificationRouter(List.of(sseChannel, pushChannel));
            AlertNotification alert = buildAlert("hcm", "HIGH");

            notificationRouter.route(alert);

            verify(sseChannel).send(alert);
            verify(pushChannel).send(alert);
        }

        @Test
        @DisplayName("No registered channels: no error")
        void noChannels_noError() {
            notificationRouter = new NotificationRouter(List.of());
            AlertNotification alert = buildAlert("hcm", "HIGH");

            assertThatCode(() -> notificationRouter.route(alert))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Push channel failure does not block SSE channel")
        void pushFailure_doesNotBlockSse() {
            notificationRouter = new NotificationRouter(List.of(sseChannel, pushChannel));
            AlertNotification alert = buildAlert("hcm", "CRITICAL");

            doThrow(new RuntimeException("Push timeout")).when(pushChannel).send(alert);
            doNothing().when(sseChannel).send(alert);

            assertThatCode(() -> notificationRouter.route(alert))
                    .doesNotThrowAnyException();

            verify(sseChannel).send(alert);
            verify(pushChannel).send(alert);
        }

        @Test
        @DisplayName("SSE channel failure does not block Push channel")
        void sseFailure_doesNotBlockPush() {
            notificationRouter = new NotificationRouter(List.of(sseChannel, pushChannel));
            AlertNotification alert = buildAlert("hcm", "CRITICAL");

            doThrow(new RuntimeException("SSE error")).when(sseChannel).send(alert);
            doNothing().when(pushChannel).send(alert);

            assertThatCode(() -> notificationRouter.route(alert))
                    .doesNotThrowAnyException();

            verify(pushChannel).send(alert);
            verify(sseChannel).send(alert);
        }

        @Test
        @DisplayName("Both channels failing does not throw to caller")
        void bothFail_noException() {
            notificationRouter = new NotificationRouter(List.of(sseChannel, pushChannel));
            AlertNotification alert = buildAlert("hcm", "CRITICAL");

            doThrow(new RuntimeException("SSE down")).when(sseChannel).send(alert);
            doThrow(new RuntimeException("Push down")).when(pushChannel).send(alert);

            assertThatCode(() -> notificationRouter.route(alert))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Single channel: route works with one channel")
        void singleChannel_routes() {
            notificationRouter = new NotificationRouter(List.of(sseChannel));
            AlertNotification alert = buildAlert("hcm", "HIGH");

            notificationRouter.route(alert);

            verify(sseChannel).send(alert);
            verifyNoInteractions(pushChannel);
        }
    }

    // ========================================================================
    // Alert data integrity
    // ========================================================================

    @Nested
    @DisplayName("Payload Integrity")
    class PayloadIntegrity {

        @Test
        @DisplayName("Alert payload fields are preserved through routing")
        void payloadFieldsPreserved() {
            notificationRouter = new NotificationRouter(List.of(pushChannel));
            AlertNotification alert = buildAlert("hcm", "CRITICAL");

            notificationRouter.route(alert);

            ArgumentCaptor<AlertNotification> captor = ArgumentCaptor.forClass(AlertNotification.class);
            verify(pushChannel).send(captor.capture());
            AlertNotification routed = captor.getValue();

            assertThat(routed.sensorId()).isEqualTo("SENSOR-AIR-001");
            assertThat(routed.severity()).isEqualTo("CRITICAL");
            assertThat(routed.tenantId()).isEqualTo("hcm");
            assertThat(routed.message()).isEqualTo("AQI reached threshold");
            assertThat(routed.alertId()).isEqualTo(1L);
            assertThat(routed.module()).isEqualTo("environment");
        }
    }

    // ========================================================================
    // Multi-tenant isolation
    // ========================================================================

    @Nested
    @DisplayName("Multi-Tenant Isolation")
    class MultiTenantIsolation {

        @Test
        @DisplayName("Tenant A alert dispatched with correct tenantId")
        void tenantA_correctTenantId() {
            notificationRouter = new NotificationRouter(List.of(pushChannel));
            AlertNotification alert = buildAlert("hcm", "HIGH");

            notificationRouter.route(alert);

            ArgumentCaptor<AlertNotification> captor = ArgumentCaptor.forClass(AlertNotification.class);
            verify(pushChannel).send(captor.capture());
            assertThat(captor.getValue().tenantId()).isEqualTo("hcm");
        }

        @Test
        @DisplayName("Tenant B alert dispatched with correct tenantId")
        void tenantB_correctTenantId() {
            notificationRouter = new NotificationRouter(List.of(pushChannel));
            AlertNotification alert = buildAlert("hn", "HIGH");

            notificationRouter.route(alert);

            ArgumentCaptor<AlertNotification> captor = ArgumentCaptor.forClass(AlertNotification.class);
            verify(pushChannel).send(captor.capture());
            assertThat(captor.getValue().tenantId()).isEqualTo("hn");
        }

        @Test
        @DisplayName("Null tenantId alert still routes without error")
        void nullTenantId_routes() {
            notificationRouter = new NotificationRouter(List.of(pushChannel));
            AlertNotification alert = new AlertNotification(
                    "SENSOR-001", "environment", "HIGH", "Alert", null, 1L);

            assertThatCode(() -> notificationRouter.route(alert))
                    .doesNotThrowAnyException();

            verify(pushChannel).send(alert);
        }
    }

    // ========================================================================
    // Concurrency
    // ========================================================================

    @Nested
    @DisplayName("Concurrency")
    class Concurrency {

        @Test
        @DisplayName("Concurrent alert events route without error")
        void concurrentEvents_noRaceCondition() {
            notificationRouter = new NotificationRouter(List.of(sseChannel, pushChannel));

            var events = IntStream.range(0, 10)
                    .mapToObj(i -> buildAlert("hcm", "HIGH"))
                    .toList();

            events.parallelStream().forEach(event -> {
                assertThatCode(() -> notificationRouter.route(event))
                        .doesNotThrowAnyException();
            });

            verify(pushChannel, times(10)).send(any(AlertNotification.class));
            verify(sseChannel, times(10)).send(any(AlertNotification.class));
        }
    }

    // ========================================================================
    // Channel ordering
    // ========================================================================

    @Nested
    @DisplayName("Channel Ordering")
    class ChannelOrdering {

        @Test
        @DisplayName("Channels are invoked in registration order")
        void channelsInvokedInOrder() {
            AtomicInteger order = new AtomicInteger(0);
            StringBuilder sb = new StringBuilder();

            NotificationChannel channel1 = new NotificationChannel() {
                @Override public void send(AlertNotification n) { sb.append("A").append(order.incrementAndGet()); }
                @Override public String getChannelName() { return "first"; }
            };
            NotificationChannel channel2 = new NotificationChannel() {
                @Override public void send(AlertNotification n) { sb.append("B").append(order.incrementAndGet()); }
                @Override public String getChannelName() { return "second"; }
            };

            notificationRouter = new NotificationRouter(List.of(channel1, channel2));
            notificationRouter.route(buildAlert("hcm", "HIGH"));

            assertThat(sb.toString()).isEqualTo("A1B2");
        }
    }
}

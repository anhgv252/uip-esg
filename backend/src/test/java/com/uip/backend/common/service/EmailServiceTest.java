package com.uip.backend.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "inviteBaseUrl", "http://app.test");
    }

    @Test
    void sendInviteEmail_sendsCorrectMessage() {
        emailService.sendInviteEmail("user@example.com", "tok-abc");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(msg.getSubject()).contains("invited");
        assertThat(msg.getText()).contains("http://app.test/accept-invite?token=tok-abc");
        assertThat(msg.getText()).contains("48 hours");
    }

    @Test
    void sendInviteEmail_mailSenderThrows_doesNotPropagateException() {
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        // EmailService swallows mail exceptions gracefully (logs warning, does not re-throw)
        assertThatCode(() -> emailService.sendInviteEmail("bad@test.com", "tok-xyz"))
                .doesNotThrowAnyException();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}

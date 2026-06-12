package com.uip.backend.common.service;

import com.uip.backend.common.logging.PiiMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.invite.base-url:http://localhost:3000}")
    private String inviteBaseUrl;

    public void sendInviteEmail(String toEmail, String token) {
        String acceptUrl = inviteBaseUrl + "/accept-invite?token=" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("You're invited to join UIP Smart City");
        message.setText("You have been invited to join UIP Smart City platform.\n\n"
                + "Click the link below to accept your invitation and set your password:\n"
                + acceptUrl + "\n\n"
                + "This invitation expires in 48 hours.");
        try {
            mailSender.send(message);
            log.info("Invite email sent: recipient={}", PiiMasker.maskEmail(toEmail));
        } catch (Exception e) {
            log.warn("Invite email not sent (mail server unavailable): recipient={}, reason={}", PiiMasker.maskEmail(toEmail), e.getMessage());
        }
    }
}

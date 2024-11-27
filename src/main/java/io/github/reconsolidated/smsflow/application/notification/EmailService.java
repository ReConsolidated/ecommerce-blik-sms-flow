package io.github.reconsolidated.smsflow.application.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@AllArgsConstructor
public class EmailService {
    private JavaMailSender mailSender;

    public void sendSimpleMessage(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("Zespół SMSFlow <kontakt@tempowaiter.pl>");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // Ustawienie treści jako HTML

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public List<String> getRecipients() {
        return List.of("gracjanpasik@gmail.com");
    }
}
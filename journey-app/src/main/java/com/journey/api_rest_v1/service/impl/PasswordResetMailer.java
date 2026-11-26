package com.journey.api_rest_v1.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import com.journey.api_rest_v1.config.MailProperties;
import com.journey.api_rest_v1.service.PasswordResetRequestedEvent;

/**
 * Emails "reset your password" links.
 *
 * <p>Runs after the request's transaction commits and on a background thread, so the request returns at the
 * same speed whether or not an account exists for the address (no way to probe for accounts by timing).</p>
 *
 * <p>Needs an SMTP server ({@code spring.mail.host}, from {@code MAIL_HOST}). Without one, nothing is sent and
 * a warning is logged; the link itself is never logged.</p>
 */
@Component
public class PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final MailProperties properties;
    private final boolean mailConfigured;

    /** Wires the (optional) mail sender and mail settings. */
    public PasswordResetMailer(ObjectProvider<JavaMailSender> mailSender, MailProperties properties,
            @Value("${spring.mail.host:}") String mailHost) {
        this.mailSender = mailSender;
        this.properties = properties;
        // spring.mail.host is always defined (possibly empty), so check the value rather than the bean.
        this.mailConfigured = mailHost != null && !mailHost.isBlank();
    }

    @Async
    @TransactionalEventListener
    public void send(PasswordResetRequestedEvent event) {
        JavaMailSender sender = mailConfigured ? mailSender.getIfAvailable() : null;
        if (sender == null) {
            log.warn("Password reset requested but no mail server is configured (MAIL_HOST); no email sent.");
            return;
        }
        String link = UriComponentsBuilder.fromUriString(properties.appBaseUrl())
                .path("/reset-password")
                .queryParam("token", event.rawToken())
                .build()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(event.email());
        message.setSubject("Reset your Journey password");
        message.setText("""
                Hi %s,

                Someone (hopefully you) asked to reset the password of your Journey account.
                Choose a new password here:

                %s

                The link works once and expires in %d minutes.
                If you did not ask for this, you can ignore this email; your password has not changed.
                """.formatted(event.displayName(), link, event.minutesValid()));
        try {
            sender.send(message);
        } catch (MailException ex) {
            log.error("Could not send the password reset email: {}", ex.getMessage());
        }
    }
}

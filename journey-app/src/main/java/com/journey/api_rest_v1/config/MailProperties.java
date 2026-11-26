package com.journey.api_rest_v1.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email settings under {@code journey.mail} (the SMTP server itself is configured with {@code spring.mail.*}).
 *
 * @param from        The sender address of emails, e.g. "Journey &lt;no-reply@example.com&gt;".
 * @param appBaseUrl  Where the frontend is served; links in emails point here (e.g. https://journey.example.com).
 */
@ConfigurationProperties(prefix = "journey.mail")
public record MailProperties(String from, String appBaseUrl) {
}

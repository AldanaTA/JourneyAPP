package com.journey.api_rest_v1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.journey.api_rest_v1.service.campaign.ChatBroker;

/**
 * Listens on the chat channel, so chat changes made on any API instance reach the streams open on this one.
 * The container reconnects on its own after a Redis outage.
 */
@Configuration
public class ChatRedisConfig {

    @Bean
    public RedisMessageListenerContainer chatListenerContainer(RedisConnectionFactory connectionFactory, ChatBroker broker) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> broker.onRedisMessage(
                new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8)), new ChannelTopic(ChatBroker.CHANNEL));
        return container;
    }
}

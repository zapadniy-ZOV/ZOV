package itmo.rshd.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory message broker for broadcasting to clients
        // Messages with these prefixes will be routed to the broker
        config.enableSimpleBroker("/topic", "/queue");

        // Messages with this prefix will be routed to @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");

        // Set prefix for user-specific messages (e.g., /user/{userId}/queue/location)
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP over WebSocket endpoint
        registry.addEndpoint("/ws")
                .setAllowedOrigins(
                        "http://localhost:5173",
                        "http://192.168.0.183:5173",
                        "http://172.18.80.1:5173",
                        "http://185.171.195.6:12348")
                .withSockJS(); // Enables SockJS fallback options
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(200 * 1024 * 1024);
        registration.setSendBufferSizeLimit(200 * 1024 * 1024);
        registration.setSendTimeLimit(20000);
    }
}
package com.racetracker.config;

import com.racetracker.security.JwtService;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties webSocketProperties;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final RaceAccessGuard raceAccessGuard;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-tracking")
                .setAllowedOrigins(webSocketProperties.getAllowedOrigins().toArray(new String[0]))
                .withSockJS();
        
        registry.addEndpoint("/ws-tracking")
                .setAllowedOrigins(webSocketProperties.getAllowedOrigins().toArray(new String[0]));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketSecurityInterceptor());
    }

    ChannelInterceptor webSocketSecurityInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticateConnect(accessor);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeSubscription(accessor);
                }
                return message;
            }
        };
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        accessor.setUser(null);
        List<String> authorization = accessor.getNativeHeader("Authorization");
        if (authorization != null && !authorization.isEmpty()) {
            String bearerToken = authorization.get(0);
            if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                String jwt = bearerToken.substring(7).trim();
                if (StringUtils.hasText(jwt)) {
                    try {
                        if (!jwtService.hasTokenType(jwt, JwtService.TOKEN_TYPE_ACCESS)) {
                            throw new AccessDeniedException("WebSocket connection requires an access token");
                        }
                        String username = jwtService.extractUsername(jwt);
                        if (StringUtils.hasText(username)) {
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                            if (jwtService.isTokenValid(jwt, userDetails)) {
                                accessor.setUser(new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities()));
                            }
                        }
                    } catch (Exception e) {
                        log.debug("WebSocket JWT validation failed");
                    }
                }
            }
        }
        if (!(accessor.getUser() instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("WebSocket connection requires authentication");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("WebSocket subscription requires authentication");
        }

        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            throw new AccessDeniedException("Subscription to destination is not allowed");
        }

        if (destination.startsWith("/topic/user.")) {
            authorizeUserSubscription(destination, authentication);
            return;
        }
        if (destination.startsWith("/topic/race.")) {
            authorizeRaceSubscription(destination, authentication);
            return;
        }
        throw new AccessDeniedException("Subscription to destination is not allowed");
    }

    private void authorizeUserSubscription(String destination, Authentication authentication) {
        UUID targetUserId = parseExactDestinationId(destination, "/topic/user.");
        UUID authenticatedUserId = userRepository.findByEmailIgnoreCase(authentication.getName())
            .map(user -> user.getId())
            .orElseThrow(() -> new AccessDeniedException("Subscription to destination is not allowed"));
        if (!targetUserId.equals(authenticatedUserId)) {
            throw new AccessDeniedException("Subscription to destination is not allowed");
        }
    }

    private void authorizeRaceSubscription(String destination, Authentication authentication) {
        UUID raceId = parseExactDestinationId(destination, "/topic/race.");
        raceAccessGuard.assertCanSubscribeToRace(raceId, authentication);
    }

    private UUID parseExactDestinationId(String destination, String prefix) {
        if (!destination.startsWith(prefix) || destination.length() <= prefix.length()) {
            throw new AccessDeniedException("Subscription to destination is not allowed");
        }
        String id = destination.substring(prefix.length());
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Subscription to destination is not allowed");
        }
    }
}

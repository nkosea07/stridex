package com.racetracker.config;

import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.JwtService;
import com.racetracker.security.RaceAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock JwtService jwtService;
    @Mock UserDetailsService userDetailsService;
    @Mock UserRepository userRepository;
    @Mock RaceRepository raceRepository;

    private RaceAccessGuard raceAccessGuard;
    private WebSocketConfig webSocketConfig;
    private MessageChannel channel;
    private final UUID aliceId = UUID.randomUUID();
    private final UUID bobId = UUID.randomUUID();
    private final UUID aliceRaceId = UUID.randomUUID();
    private final UUID bobRaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        raceAccessGuard = new RaceAccessGuard(raceRepository);
        webSocketConfig = new WebSocketConfig(
            new WebSocketProperties(), jwtService, userDetailsService, userRepository, raceAccessGuard);
        channel = mock(MessageChannel.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void connectRequiresAValidBearerToken() {
        assertDenied(message(StompCommand.CONNECT, null, null));

        UserDetails alice = userDetails("alice@example.com", "ROLE_RACE_ADMIN");
        when(jwtService.hasTokenType("valid-token", JwtService.TOKEN_TYPE_ACCESS)).thenReturn(true);
        when(jwtService.extractUsername("valid-token")).thenReturn("alice@example.com");
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(alice);
        when(jwtService.isTokenValid("valid-token", alice)).thenReturn(true);

        Message<?> validConnect = message(StompCommand.CONNECT, null, "Bearer valid-token");
        assertThat(StompHeaderAccessor.wrap(validConnect).getNativeHeader("Authorization"))
            .containsExactly("Bearer valid-token");
        Message<?> result = intercept(validConnect);
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);

        assertThat(accessor.getUser()).isInstanceOf(Authentication.class);
        assertThat(accessor.getUser().getName()).isEqualTo("alice@example.com");
    }

    @Test
    void connectRejectsRefreshTokens() {
        when(jwtService.hasTokenType("refresh-token", JwtService.TOKEN_TYPE_ACCESS)).thenReturn(false);

        assertDenied(message(StompCommand.CONNECT, null, "Bearer refresh-token"));
    }

    @Test
    void connectDoesNotTrustAStalePrincipalWithoutAValidBearerToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setUser(authentication("alice@example.com", "ROLE_RACE_ADMIN"));
        accessor.setLeaveMutable(true);

        assertDenied(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
    }

    @Test
    void userSubscriptionIsLimitedToTheAuthenticatedUser() {
        Authentication alice = authentication("alice@example.com", "ROLE_ATHLETE");
        when(userRepository.findByEmailIgnoreCase("alice@example.com"))
            .thenReturn(Optional.of(User.builder().id(aliceId).email("alice@example.com").build()));

        Message<?> result = intercept(subscription("/topic/user." + aliceId, alice));
        assertThat(result).isNotNull();

        assertDenied(subscription("/topic/user." + bobId, alice));
    }

    @Test
    void raceSubscriptionUsesRaceOwnerAndAdminAuthorization() {
        Race aliceRace = ownedRace(aliceRaceId, "alice@example.com");
        Race bobRace = ownedRace(bobRaceId, "bob@example.com");
        when(raceRepository.findByIdForAuthorization(aliceRaceId)).thenReturn(Optional.of(aliceRace));
        when(raceRepository.findByIdForAuthorization(bobRaceId)).thenReturn(Optional.of(bobRace));

        Authentication alice = authentication("alice@example.com", "ROLE_RACE_ADMIN");
        assertThat(intercept(subscription("/topic/race." + aliceRaceId, alice))).isNotNull();
        assertDenied(subscription("/topic/race." + bobRaceId, alice));
    }

    @Test
    void superAdminMaySubscribeToAnyKnownRace() {
        Race race = ownedRace(aliceRaceId, "alice@example.com");
        when(raceRepository.findByIdForAuthorization(aliceRaceId)).thenReturn(Optional.of(race));

        assertThat(intercept(subscription(
            "/topic/race." + aliceRaceId,
            authentication("root@example.com", "ROLE_SUPER_ADMIN")))).isNotNull();
    }

    @Test
    void raceOperationsStaffMaySubscribeToKnownRaceTopics() {
        Race race = ownedRace(aliceRaceId, "alice@example.com");
        when(raceRepository.findByIdForAuthorization(aliceRaceId)).thenReturn(Optional.of(race));

        assertThat(intercept(subscription(
            "/topic/race." + aliceRaceId,
            authentication("official@example.com", "ROLE_OFFICIAL")))).isNotNull();
        assertThat(intercept(subscription(
            "/topic/race." + aliceRaceId,
            authentication("volunteer@example.com", "ROLE_VOLUNTEER")))).isNotNull();
    }

    @Test
    void participantAndAnonymousUsersCannotSubscribeToRaceTopics() {
        assertDenied(subscription(
            "/topic/race." + aliceRaceId,
            authentication("alice@example.com", "ROLE_ATHLETE")));
        assertDenied(subscription("/topic/race." + aliceRaceId, null));
    }

    @Test
    void onlyExactUserAndRaceDestinationsAreAccepted() {
        Authentication alice = authentication("alice@example.com", "ROLE_RACE_ADMIN");

        assertDenied(subscription(null, alice));
        assertDenied(subscription("/topic/*", alice));
        assertDenied(subscription("/topic/race." + aliceRaceId + ".extra", alice));
        assertDenied(subscription("/queue/user." + aliceId, alice));
        assertDenied(subscription("/app/race." + aliceRaceId, alice));
        assertDenied(subscription("/topic/user.not-a-uuid", alice));
    }

    private Message<?> intercept(Message<?> message) {
        return webSocketConfig.webSocketSecurityInterceptor().preSend(message, channel);
    }

    private void assertDenied(Message<?> message) {
        assertThatThrownBy(() -> intercept(message))
            .isInstanceOf(AccessDeniedException.class);
    }

    private Message<?> message(StompCommand command, String destination, String authorization) {
        Map<String, List<String>> nativeHeaders = StringUtils.hasText(authorization)
            ? Map.of("Authorization", List.of(authorization))
            : Map.of();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command, nativeHeaders);
        if (StringUtils.hasText(destination)) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> subscription(String destination, Authentication authentication) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authentication != null) {
            accessor.setUser(authentication);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Authentication authentication(String email, String role) {
        return new UsernamePasswordAuthenticationToken(
            userDetails(email, role), null, List.of(new SimpleGrantedAuthority(role)));
    }

    private UserDetails userDetails(String email, String role) {
        return org.springframework.security.core.userdetails.User.withUsername(email)
            .password("unused")
            .authorities(role)
            .build();
    }

    private Race ownedRace(UUID id, String ownerEmail) {
        return Race.builder()
            .id(id)
            .createdBy(User.builder().id(UUID.randomUUID()).email(ownerEmail).build())
            .admins(Set.of())
            .build();
    }
}

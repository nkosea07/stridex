package com.racetracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.repository.PushDeviceRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApnsPushNotificationServiceTest {

    private static final String TOKEN = "a".repeat(64);
    private static final String SECOND_TOKEN = "b".repeat(64);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PushDeviceRepository devices = mock(PushDeviceRepository.class);
    private final HttpClient http = mock(HttpClient.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-23T08:00:00Z"));
    private KeyPair keys;
    private RaceTrackerProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keys = generator.generateKeyPair();
        properties = new RaceTrackerProperties();
        properties.getPush().setEnabled(true);
        RaceTrackerProperties.Apns apns = properties.getPush().getApns();
        apns.setKeyId("KEY1234567");
        apns.setTeamId("TEAM765432");
        apns.setPrivateKey(pem(keys));
    }

    @Test
    void sendsASignedAlertWithTheDataWhereTheAppReadsIt() throws Exception {
        HttpResponse<String> ok = response(200, "");
        doReturn(ok).when(http).send(any(), any());
        ApnsPushNotificationService service = start();

        int accepted = service.sendToTokens(List.of(TOKEN), "Checkpoint", "Runner passed 10K", Map.of("type", "CHECKPOINT"));

        assertThat(accepted).isEqualTo(1);
        HttpRequest request = sentRequests(1).get(0);
        assertThat(request.uri().toString()).isEqualTo(ApnsPushNotificationService.PRODUCTION_HOST + "/3/device/" + TOKEN);
        assertThat(request.headers().firstValue("apns-topic")).contains("com.racetracker.app");
        assertThat(request.headers().firstValue("apns-push-type")).contains("alert");
        assertThat(request.headers().firstValue("apns-priority")).contains("10");
        Jws<Claims> jwt = Jwts.parser().verifyWith(keys.getPublic()).build()
            .parseSignedClaims(request.headers().firstValue("authorization").orElseThrow().substring("bearer ".length()));
        assertThat(jwt.getHeader().getKeyId()).isEqualTo("KEY1234567");
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo("ES256");
        assertThat(jwt.getPayload().getIssuer()).isEqualTo("TEAM765432");
        JsonNode payload = objectMapper.readTree(bodyOf(request));
        assertThat(payload.at("/aps/alert/title").asText()).isEqualTo("Checkpoint");
        assertThat(payload.at("/aps/alert/body").asText()).isEqualTo("Runner passed 10K");
        assertThat(payload.at("/body/type").asText()).isEqualTo("CHECKPOINT");
    }

    @Test
    void usesTheSandboxHostForDevelopmentBuilds() throws Exception {
        properties.getPush().getApns().setSandbox(true);
        HttpResponse<String> ok = response(200, "");
        doReturn(ok).when(http).send(any(), any());

        start().sendToTokens(List.of(TOKEN), "t", "b", Map.of());

        assertThat(sentRequests(1).get(0).uri().toString()).startsWith(ApnsPushNotificationService.SANDBOX_HOST);
    }

    @Test
    void prunesTokensApnsReportsAsGoneOrInvalid() throws Exception {
        HttpResponse<String> gone = response(410, "{\"reason\":\"Unregistered\"}");
        HttpResponse<String> bad = response(400, "{\"reason\":\"BadDeviceToken\"}");
        doReturn(gone).doReturn(bad).when(http).send(any(), any());

        int accepted = start().sendToTokens(List.of(TOKEN, SECOND_TOKEN), "t", "b", Map.of());

        assertThat(accepted).isZero();
        verify(devices).deleteByToken(TOKEN);
        verify(devices).deleteByToken(SECOND_TOKEN);
    }

    @Test
    void keepsTokensForOtherRejections() throws Exception {
        HttpResponse<String> tooBig = response(413, "{\"reason\":\"PayloadTooLarge\"}");
        doReturn(tooBig).when(http).send(any(), any());

        assertThat(start().sendToTokens(List.of(TOKEN), "t", "b", Map.of())).isZero();
        verify(devices, never()).deleteByToken(any());
    }

    @Test
    void dropsMalformedTokensWithoutCallingApple() throws Exception {
        int accepted = start().sendToTokens(List.of("ExponentPushToken[abc]"), "t", "b", Map.of());

        assertThat(accepted).isZero();
        verify(devices).deleteByToken("ExponentPushToken[abc]");
        verify(http, never()).send(any(), any());
    }

    @Test
    void transportFailureIsRetryableOnlyWhenNothingWasAccepted() throws Exception {
        ApnsPushNotificationService service = start();
        doThrow(new IOException("connection reset")).when(http).send(any(), any());
        assertThatThrownBy(() -> service.sendToTokens(List.of(TOKEN), "t", "b", Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Push provider request failed");

        HttpResponse<String> ok = response(200, "");
        doThrow(new IOException("connection reset")).doReturn(ok).when(http).send(any(), any());
        assertThat(service.sendToTokens(List.of(TOKEN, SECOND_TOKEN), "t", "b", Map.of())).isEqualTo(1);
    }

    @Test
    void reusesTheProviderTokenUntilItNearsExpiry() throws Exception {
        HttpResponse<String> ok = response(200, "");
        doReturn(ok).when(http).send(any(), any());
        ApnsPushNotificationService service = start();

        service.sendToTokens(List.of(TOKEN), "t", "b", Map.of());
        clock.advance(ApnsPushNotificationService.PROVIDER_TOKEN_LIFETIME.minusMinutes(1));
        service.sendToTokens(List.of(TOKEN), "t", "b", Map.of());
        clock.advance(ApnsPushNotificationService.PROVIDER_TOKEN_LIFETIME);
        service.sendToTokens(List.of(TOKEN), "t", "b", Map.of());

        List<String> authorizations = sentRequests(3).stream()
            .map(request -> request.headers().firstValue("authorization").orElseThrow())
            .toList();
        assertThat(authorizations.get(1)).isEqualTo(authorizations.get(0));
        assertThat(authorizations.get(2)).isNotEqualTo(authorizations.get(0));
    }

    @Test
    void regeneratesTheProviderTokenAfterAppleRejectsIt() throws Exception {
        HttpResponse<String> expired = response(403, "{\"reason\":\"ExpiredProviderToken\"}");
        HttpResponse<String> ok = response(200, "");
        doReturn(expired).doReturn(ok).when(http).send(any(), any());
        ApnsPushNotificationService service = start();

        service.sendToTokens(List.of(TOKEN), "t", "b", Map.of());
        clock.advance(java.time.Duration.ofSeconds(1));
        service.sendToTokens(List.of(TOKEN), "t", "b", Map.of());

        List<HttpRequest> requests = sentRequests(2);
        assertThat(requests.get(1).headers().firstValue("authorization"))
            .isNotEqualTo(requests.get(0).headers().firstValue("authorization"));
        verify(devices, never()).deleteByToken(any());
    }

    @Test
    void keepsTheProviderTokenWhenAppleSaysItIsInvalid() throws Exception {
        HttpResponse<String> invalid = response(403, "{\"reason\":\"InvalidProviderToken\"}");
        doReturn(invalid).when(http).send(any(), any());
        ApnsPushNotificationService service = start();

        service.sendToTokens(List.of(TOKEN), "t", "b", Map.of());
        clock.advance(java.time.Duration.ofSeconds(1));
        service.sendToTokens(List.of(TOKEN), "t", "b", Map.of());

        List<HttpRequest> requests = sentRequests(2);
        assertThat(requests.get(1).headers().firstValue("authorization"))
            .isEqualTo(requests.get(0).headers().firstValue("authorization"));
        verify(devices, never()).deleteByToken(any());
    }

    @Test
    void staysUnavailableWithoutCompleteCredentialsOrAValidKey() throws Exception {
        properties.getPush().getApns().setTeamId("");
        ApnsPushNotificationService missingTeam = start();
        assertThat(missingTeam.isAvailable()).isFalse();
        assertThat(missingTeam.sendToTokens(List.of(TOKEN), "t", "b", Map.of())).isZero();

        properties.getPush().getApns().setTeamId("TEAM765432");
        properties.getPush().getApns().setPrivateKey("not a key");
        assertThat(start().isAvailable()).isFalse();
        verifyNoInteractions(http);
    }

    @Test
    void acceptsAKeyPastedOnOneLineAndOnlyHandlesIos() {
        properties.getPush().getApns().setPrivateKey(pem(keys).replace("\n", "\\n"));
        ApnsPushNotificationService service = start();

        assertThat(service.isAvailable()).isTrue();
        assertThat(service.supports("ios")).isTrue();
        assertThat(service.supports("android")).isFalse();
        assertThat(service.supports(null)).isFalse();
    }

    private ApnsPushNotificationService start() {
        ApnsPushNotificationService service = new ApnsPushNotificationService(properties, devices, objectMapper, http, clock);
        service.initialize();
        return service;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private List<HttpRequest> sentRequests(int expected) throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(expected)).send(captor.capture(), any());
        return captor.getAllValues();
    }

    private static String pem(KeyPair keys) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(keys.getPrivate().getEncoded());
        String label = "PRIVATE" + " KEY";
        return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
    }

    private static String bodyOf(HttpRequest request) throws InterruptedException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.write(chunk, 0, chunk.length);
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        assertThat(done.await(1, TimeUnit.SECONDS)).isTrue();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(java.time.Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

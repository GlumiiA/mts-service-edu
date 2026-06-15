package ru.aigul.mts_service.integration.taiga.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.aigul.mts_service.exception.TaigaIntegrationException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps a Taiga access token usable across the application lifetime.
 * Taiga issues short-lived JWT access tokens, so a statically configured token
 * eventually expires ("Token is invalid or expired"). This manager refreshes it
 * on demand via {@code POST /api/v1/auth/refresh} using a long-lived refresh token.
 */
@Slf4j
@Component
public class TaigaAuthTokenManager {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient authWebClient;
    private final long requestTimeoutMs;
    private final AtomicReference<String> accessToken;
    private final AtomicReference<String> refreshToken;

    public TaigaAuthTokenManager(
            @Value("${app.taiga.base-url:http://localhost:9000}") String baseUrl,
            @Value("${app.taiga.api-token:}") String initialAccessToken,
            @Value("${app.taiga.refresh-token:}") String refreshToken,
            @Value("${app.taiga.request-timeout-ms:10000}") long requestTimeoutMs) {
        this.authWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.requestTimeoutMs = requestTimeoutMs;
        this.accessToken = new AtomicReference<>(blankToNull(initialAccessToken));
        this.refreshToken = new AtomicReference<>(blankToNull(refreshToken));
    }

    public String getAccessToken() {
        String token = accessToken.get();
        return token != null ? token : refreshAccessToken();
    }

    public synchronized String refreshAccessToken() {
        String currentRefreshToken = refreshToken.get();
        if (currentRefreshToken == null) {
            throw new TaigaIntegrationException("Taiga refresh token is not configured (app.taiga.refresh-token)");
        }

        try {
            String body = authWebClient.post()
                    .uri("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("refresh", currentRefreshToken))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(requestTimeoutMs));

            if (body == null || body.isBlank()) {
                throw new TaigaIntegrationException("Taiga auth refresh returned an empty response");
            }

            JsonNode response = objectMapper.readTree(body);

            String newAccessToken = response.path("access").asText(null);
            if (newAccessToken == null || newAccessToken.isBlank()) {
                throw new TaigaIntegrationException("Taiga auth refresh response doesn't contain an access token");
            }

            String newRefreshToken = response.path("refresh").asText(null);

            accessToken.set(newAccessToken);
            if (newRefreshToken != null && !newRefreshToken.isBlank()) {
                refreshToken.set(newRefreshToken);
            }

            log.info("Taiga access token refreshed successfully");
            return newAccessToken;
        } catch (WebClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            throw new TaigaIntegrationException(
                    "Taiga auth refresh failed: " + (!body.isBlank() ? body : ex.getMessage()), ex);
        } catch (TaigaIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TaigaIntegrationException("Taiga auth refresh failed: " + ex.getMessage(), ex);
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}

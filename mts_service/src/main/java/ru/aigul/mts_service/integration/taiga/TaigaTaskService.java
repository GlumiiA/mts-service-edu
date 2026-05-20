package ru.aigul.mts_service.integration.taiga;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.aigul.mts_service.exception.TaigaIntegrationException;
import ru.aigul.mts_service.model.Application;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaigaTaskService {

    private final WebClient taigaWebClient;

    @Value("${app.taiga.api-token:}")
    private String apiToken;

    @Value("${app.taiga.project-id:0}")
    private long projectId;

    @Value("${app.taiga.request-timeout-ms:10000}")
    private long requestTimeoutMs;

    public Optional<Long> createUserStoryForApplication(Application application) {
        if (apiToken == null || apiToken.isBlank() || projectId <= 0) {
            log.warn("Taiga task creation skipped: app.taiga.api-token or app.taiga.project-id is not configured");
            return Optional.empty();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project", projectId);
        payload.put("subject", buildSubject(application));
        payload.put("description", buildDescription(application));

        JsonNode root = postJson("/api/v1/userstories", payload);
        long taigaId = root.path("id").asLong(0L);
        if (taigaId <= 0) {
            throw new TaigaIntegrationException("Taiga response doesn't contain created userstory id");
        }

        log.info("Taiga userstory created for applicationId={}, taigaTaskId={}", application.getId(), taigaId);
        return Optional.of(taigaId);
    }

    private JsonNode postJson(String path, Object payload) {
        try {
            JsonNode root = taigaWebClient.post()
                    .uri(path)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofMillis(requestTimeoutMs));

            if (root == null) {
                throw new TaigaIntegrationException("Taiga returned an empty response");
            }
            return root;
        } catch (WebClientResponseException ex) {
            String response = ex.getResponseBodyAsString();
            String details = response != null && !response.isBlank() ? response : ex.getMessage();
            throw new TaigaIntegrationException("Taiga request failed: " + details, ex);
        } catch (TaigaIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TaigaIntegrationException("Taiga request failed: " + ex.getMessage(), ex);
        }
    }

    private String buildSubject(Application application) {
        return "Application #" + application.getId() + " - " + application.getTariff().getName();
    }

    private String buildDescription(Application application) {
        StringBuilder sb = new StringBuilder();
        sb.append("MTS application created automatically").append('\n');
        sb.append("Application ID: ").append(application.getId()).append('\n');
        sb.append("User: ").append(application.getUser().getEmail()).append('\n');
        sb.append("Tariff: ").append(application.getTariff().getName()).append('\n');
        sb.append("Address: ").append(application.getAddress()).append('\n');
        sb.append("Locked price: ").append(application.getLockedPrice());
        return sb.toString();
    }
}

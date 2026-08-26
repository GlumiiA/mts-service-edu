package ru.aigul.mts_service.integration.jca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.aigul.mts_service.exception.TaigaIntegrationException;

import java.time.Duration;
import java.util.Map;
@Slf4j
public class TaigaConnectionImpl implements TaigaConnection {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TaigaManagedConnection managedConnection;
    private final WebClient webClient;
    private final TaigaConnectionSpec spec;
    private boolean closed = false;

    public TaigaConnectionImpl(TaigaManagedConnection managedConnection, WebClient webClient, TaigaConnectionSpec spec) {
        this.managedConnection = managedConnection;
        this.webClient = webClient;
        this.spec = spec;
    }

    @Override
    public JsonNode createUserStory(Map<String, Object> payload) {
        if (closed) {
            throw new TaigaIntegrationException("Connection is closed");
        }

        if (spec.getApiToken() == null || spec.getApiToken().isBlank() || spec.getProjectId() <= 0) {
            log.warn("Taiga user story creation skipped: api-token or project-id is not configured");
            return null;
        }

        try {
            JsonNode root = postJson("/api/v1/userstories", payload);
            long taigaId = root.path("id").asLong(0L);
            if (taigaId <= 0) {
                throw new TaigaIntegrationException("Taiga response doesn't contain created userstory id");
            }
            log.info("Taiga userstory created via JCA connector, taigaTaskId={}", taigaId);
            return root;
        } catch (TaigaIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TaigaIntegrationException("Taiga user story creation failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public JsonNode getUserStory(long id) {
        if (closed) {
            throw new TaigaIntegrationException("Connection is closed");
        }
        return getJson("/api/v1/userstories/" + id);
    }

    @Override
    public JsonNode updateUserStory(long id, Map<String, Object> payload) {
        if (closed) {
            throw new TaigaIntegrationException("Connection is closed");
        }
        return patchJson("/api/v1/userstories/" + id, payload);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            managedConnection.fireConnectionClosedEvent();
            log.debug("TaigaConnection closed via JCA connector");
        }
    }

    void invalidate() {
        closed = true;
    }

    void setManagedConnection(TaigaManagedConnection managedConnection) {
        this.managedConnection = managedConnection;
    }

    private JsonNode postJson(String path, Object payload) {
        try {
            String response = webClient.post()
                    .uri(path)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(spec.getRequestTimeoutMs()));

            if (response == null || response.isBlank()) {
                throw new TaigaIntegrationException("Taiga returned an empty response");
            }
            return objectMapper.readTree(response);
        } catch (WebClientResponseException ex) {
            String response = ex.getResponseBodyAsString();
            String details = !response.isBlank() ? response : ex.getMessage();
            throw new TaigaIntegrationException("Taiga request failed: " + details, ex);
        } catch (TaigaIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TaigaIntegrationException("Taiga request failed: " + ex.getMessage(), ex);
        }
    }

    private JsonNode getJson(String path) {
        try {
            String response = webClient.get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(spec.getRequestTimeoutMs()));

            if (response == null || response.isBlank()) {
                throw new TaigaIntegrationException("Taiga returned an empty response");
            }
            return objectMapper.readTree(response);
        } catch (WebClientResponseException ex) {
            String response = ex.getResponseBodyAsString();
            String details = !response.isBlank() ? response : ex.getMessage();
            throw new TaigaIntegrationException("Taiga request failed: " + details, ex);
        } catch (TaigaIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TaigaIntegrationException("Taiga request failed: " + ex.getMessage(), ex);
        }
    }

    private JsonNode patchJson(String path, Object payload) {
        try {
            String response = webClient.patch()
                    .uri(path)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(spec.getRequestTimeoutMs()));

            if (response == null || response.isBlank()) {
                throw new TaigaIntegrationException("Taiga returned an empty response");
            }
            return objectMapper.readTree(response);
        } catch (WebClientResponseException ex) {
            String response = ex.getResponseBodyAsString();
            String details = !response.isBlank() ? response : ex.getMessage();
            throw new TaigaIntegrationException("Taiga request failed: " + details, ex);
        } catch (TaigaIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TaigaIntegrationException("Taiga request failed: " + ex.getMessage(), ex);
        }
    }
}

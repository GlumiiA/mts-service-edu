package ru.aigul.mts_service.integration1С;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;


@Slf4j
@Service
public class OneCCCIInteraction {

    private final WebClient oneCWebClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${app.one-c.create-application-path}")
    private String createApplicationPath;

    @Value("${app.one-c.check-status-path}")
    private String checkStatusPath;

    @Value("${app.one-c.request-timeout-ms:30000}")
    private long requestTimeoutMs;

    private boolean closed = false;

    public OneCCCIInteraction(@Qualifier("oneCWebClient") WebClient oneCWebClient,
                              com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.oneCWebClient = oneCWebClient;
        this.objectMapper = objectMapper;
    }

    public OneCCCIRecord execute(OneCCCIInteractionSpec spec, OneCCCIRecord input) {
        if (closed) {
            throw new RuntimeException("Interaction is closed");
        }

        if (spec == null) {
            throw new RuntimeException("Invalid InteractionSpec");
        }

        log.debug("Executing 1C interaction: operation={}", spec.getOperationType());

        OneCCCIRecord result = executeOperation(spec, input);
        
        return result;
    }


    private OneCCCIRecord executeOperation(OneCCCIInteractionSpec spec, OneCCCIRecord input) {
        try {
            return switch (spec.getOperationType()) {
                case "CREATE_APPLICATION" -> createApplicationInOneC(input);
                case "CHECK_APPLICATION" -> checkApplicationStatusInOneC(input);
                default -> throw new RuntimeException("Unknown operation: " + spec.getOperationType());
            };
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute operation: " + e.getMessage(), e);
        }
    }

    private OneCCCIRecord createApplicationInOneC(OneCCCIRecord input) {
        log.info("Creating application in 1C: {}", input.getPayload());

        try {
            OneCApplicationDTO request = objectMapper.readValue(input.getPayload(), OneCApplicationDTO.class);
            OneCResponseDTO response = oneCWebClient.post()
                .uri(createApplicationPath)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchangeToMono(clientResponse -> readOneCResponse(clientResponse, "CREATE_APPLICATION"))
                .block(Duration.ofMillis(requestTimeoutMs));

            if (response == null) {
                throw new RuntimeException("Empty response from 1C when creating application");
            }

            return OneCCCIRecord.builder()
                .operationType("CREATE_APPLICATION_RESPONSE")
                .externalId(response.getExternalId())
                .payload(objectMapper.writeValueAsString(response))
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create application in 1C: " + e.getMessage(), e);
        }
    }


    private OneCCCIRecord checkApplicationStatusInOneC(OneCCCIRecord input) {
        log.info("Checking application status in 1C: {}", input.getPayload());

        try {
            OneCResponseDTO response = oneCWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(checkStatusPath)
                    .queryParam("externalId", input.getExternalId())
                    .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono(clientResponse -> readOneCResponse(clientResponse, "CHECK_APPLICATION"))
                .block(Duration.ofMillis(requestTimeoutMs));

            if (response == null) {
                throw new RuntimeException("Empty response from 1C when checking application status");
            }

            return OneCCCIRecord.builder()
                .operationType("CHECK_APPLICATION_RESPONSE")
                .externalId(response.getExternalId() != null ? response.getExternalId() : input.getExternalId())
                .payload(objectMapper.writeValueAsString(response))
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to check application status in 1C: " + e.getMessage(), e);
        }
    }

    private Mono<OneCResponseDTO> readOneCResponse(ClientResponse clientResponse, String operationType) {
        return clientResponse.bodyToMono(OneCResponseDTO.class)
            .defaultIfEmpty(OneCResponseDTO.builder()
                .status(clientResponse.statusCode().is2xxSuccessful() ? "SUCCESS" : "FAILURE")
                .errorCode(clientResponse.statusCode().is2xxSuccessful()
                    ? null
                    : defaultErrorCode(clientResponse.statusCode().value()))
                .errorMessage(clientResponse.statusCode().is2xxSuccessful()
                    ? null
                    : "1C returned HTTP " + clientResponse.statusCode().value())
                .build())
            .map(body -> {
                if (body.getStatus() == null) {
                    body.setStatus(clientResponse.statusCode().is2xxSuccessful() ? "SUCCESS" : "FAILURE");
                }
                if (body.getErrorCode() == null && !clientResponse.statusCode().is2xxSuccessful()) {
                    body.setErrorCode(defaultErrorCode(clientResponse.statusCode().value()));
                }
                if (body.getErrorMessage() == null && !clientResponse.statusCode().is2xxSuccessful()) {
                    body.setErrorMessage("1C returned HTTP " + clientResponse.statusCode().value());
                }
                if (body.getTimestamp() == null) {
                    body.setTimestamp(LocalDateTime.now().toString());
                }
                log.debug("Received 1C response for {}: status={}, externalId={}, errorCode={}",
                    operationType, body.getStatus(), body.getExternalId(), body.getErrorCode());
                return body;
            });
    }

    private String defaultErrorCode(int httpStatus) {
        if (httpStatus == 409) {
            return "INSUFFICIENT_FUNDS";
        }
        if (httpStatus == 422) {
            return "VALIDATION_ERROR";
        }
        if (httpStatus >= 500) {
            return "SERVER_ERROR";
        }
        return "HTTP_" + httpStatus;
    }

    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}



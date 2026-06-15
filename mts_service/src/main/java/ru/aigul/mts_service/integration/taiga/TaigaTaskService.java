package ru.aigul.mts_service.integration.taiga;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.resource.ResourceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aigul.mts_service.exception.TaigaIntegrationException;
import ru.aigul.mts_service.integration.jca.TaigaConnection;
import ru.aigul.mts_service.integration.jca.TaigaConnectionFactory;
import ru.aigul.mts_service.model.Application;
import ru.aigul.mts_service.model.ApplicationStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class TaigaTaskService {

    private final TaigaConnectionFactory taigaConnectionFactory;

    @Value("${app.taiga.api-token:}")
    private String apiToken;

    @Value("${app.taiga.project-id:0}")
    private long projectId;

    @Value("${app.taiga.status.new-id:0}")
    private long newStatusId;

    @Value("${app.taiga.status.in-progress-id:0}")
    private long inProgressStatusId;

    @Value("${app.taiga.status.ready-for-test-id:0}")
    private long readyForTestStatusId;

    @Value("${app.taiga.status.done-id:0}")
    private long doneStatusId;

    @Value("${app.taiga.status.archived-id:0}")
    private long archivedStatusId;

    public TaigaTaskService(TaigaConnectionFactory taigaConnectionFactory) {
        this.taigaConnectionFactory = taigaConnectionFactory;
    }

    public Optional<Long> createUserStoryForApplication(Application application) {
        if (apiToken == null || apiToken.isBlank() || projectId <= 0) {
            log.warn("Taiga task creation skipped: app.taiga.api-token or app.taiga.project-id is not configured");
            return Optional.empty();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project", projectId);
        payload.put("subject", buildSubject(application));
        payload.put("description", buildDescription(application));
        if (newStatusId > 0) {
            payload.put("status", newStatusId);
        }

        try {
            TaigaConnection connection = taigaConnectionFactory.getConnection();
            try {
                JsonNode root = connection.createUserStory(payload);

                if (root == null) {
                    throw new TaigaIntegrationException("Taiga returned no response");
                }

                long taigaId = root.path("id").asLong(0L);
                if (taigaId <= 0) {
                    throw new TaigaIntegrationException("Taiga response doesn't contain created userstory id");
                }

                log.info("Taiga userstory created for applicationId={}, taigaTaskId={} via JCA connector",
                        application.getId(), taigaId);
                return Optional.of(taigaId);
            } finally {
                connection.close();
            }
        } catch (ResourceException ex) {
            log.error("JCA resource error while creating Taiga user story: {}", ex.getMessage(), ex);
            throw new TaigaIntegrationException("JCA resource error: " + ex.getMessage(), ex);
        } catch (TaigaIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error while creating Taiga user story: {}", ex.getMessage(), ex);
            throw new TaigaIntegrationException("Failed to create Taiga user story: " + ex.getMessage(), ex);
        }
    }

    public boolean isWorkflowConfigured() {
        return newStatusId > 0
                && inProgressStatusId > 0
                && readyForTestStatusId > 0
                && doneStatusId > 0
                && archivedStatusId > 0;
    }

    public boolean isInProgressStatus(long statusId) {
        return statusId == inProgressStatusId;
    }

    public boolean isNewStatus(long statusId) {
        return statusId == newStatusId;
    }

    public boolean isReadyForTestStatus(long statusId) {
        return statusId == readyForTestStatusId;
    }

    public boolean isDoneStatus(long statusId) {
        return statusId == doneStatusId;
    }

    public boolean isArchivedStatus(long statusId) {
        return statusId == archivedStatusId;
    }

    public void moveApplicationToCurrentStatus(Application application, String comment) {
        Long taigaTaskId = application.getTaigaTaskId();
        if (taigaTaskId == null) {
            log.warn("Cannot sync Taiga status: applicationId={} has no taigaTaskId", application.getId());
            return;
        }
        moveUserStoryToStatus(taigaTaskId, statusIdFor(application.getStatus()), comment);
    }

    public void moveApplicationToArchived(Application application, String comment) {
        if (application.getTaigaTaskId() == null) {
            return;
        }
        moveUserStoryToStatus(application.getTaigaTaskId(), archivedStatusId, comment);
    }

    public void moveApplicationToDone(Application application, String comment) {
        if (application.getTaigaTaskId() == null) {
            return;
        }
        moveUserStoryToStatus(application.getTaigaTaskId(), doneStatusId, comment);
    }

    public void moveUserStoryToStatus(long userStoryId, long statusId, String comment) {
        if (apiToken == null || apiToken.isBlank() || projectId <= 0) {
            log.warn("Taiga status update skipped: app.taiga.api-token or app.taiga.project-id is not configured");
            return;
        }
        if (statusId <= 0) {
            log.warn("Taiga status update skipped: status id is not configured");
            return;
        }

        try {
            TaigaConnection connection = taigaConnectionFactory.getConnection();
            try {
                JsonNode current = connection.getUserStory(userStoryId);
                long version = current.path("version").asLong(0L);
                if (version <= 0) {
                    throw new TaigaIntegrationException("Taiga userstory response doesn't contain version");
                }

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("version", version);
                payload.put("status", statusId);
                if (comment != null && !comment.isBlank()) {
                    payload.put("comment", comment);
                }

                connection.updateUserStory(userStoryId, payload);
                log.info("Taiga userstory status updated: taigaTaskId={}, statusId={}", userStoryId, statusId);
            } finally {
                connection.close();
            }
        } catch (ResourceException ex) {
            throw new TaigaIntegrationException("JCA resource error: " + ex.getMessage(), ex);
        }
    }

    private long statusIdFor(ApplicationStatus status) {
        return switch (status) {
            case PENDING_TAIGA_SYNC, PENDING -> newStatusId;
            case PROCESSING -> inProgressStatusId;
            case APPROVED -> readyForTestStatusId;
            case CONNECTED -> doneStatusId;
            case REJECTED, FAILED_EXTERNAL -> archivedStatusId;
        };
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

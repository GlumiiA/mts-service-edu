package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.exception.ApplicationNotFoundException;
import ru.aigul.mts_service.exception.TaigaIntegrationException;
import ru.aigul.mts_service.integration.taiga.TaigaTaskService;
import ru.aigul.mts_service.model.Application;
import ru.aigul.mts_service.model.ApplicationStatus;
import ru.aigul.mts_service.repository.ApplicationRepository;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationTaigaSyncWorkflowService {

    private final ApplicationRepository applicationRepository;
    private final TaigaTaskService taigaTaskService;

    @Value("${app.messaging.taiga-sync.max-delivery-attempts:5}")
    private int maxDeliveryAttempts;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void createStoryForApplication(Long applicationId, int deliveryAttempt) {
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (application.getTaigaTaskId() != null) {
            log.info("Taiga story already exists for application {}, skipping", applicationId);
            if (application.getStatus() == ApplicationStatus.PENDING_TAIGA_SYNC) {
                application.setStatus(ApplicationStatus.PENDING);
                applicationRepository.save(application);
            }
            return;
        }

        if (application.getStatus() != ApplicationStatus.PENDING_TAIGA_SYNC) {
            log.info("Application {} no longer awaits Taiga sync (status={}), skipping",
                    applicationId, application.getStatus());
            return;
        }

        try {
            Optional<Long> taigaTaskId = taigaTaskService.createUserStoryForApplication(application);
            if (taigaTaskId.isEmpty()) {
                throw new TaigaIntegrationException("Taiga task was not created for applicationId=" + applicationId);
            }
            application.setTaigaTaskId(taigaTaskId.get());
            application.setStatus(ApplicationStatus.PENDING);
            applicationRepository.save(application);
            log.info("Taiga story created for application {}: taigaTaskId={}", applicationId, taigaTaskId.get());
        } catch (TaigaIntegrationException ex) {
            if (deliveryAttempt >= maxDeliveryAttempts) {
                log.error("Giving up Taiga story creation for application {} after {} attempts: {}",
                        applicationId, deliveryAttempt, ex.getMessage(), ex);
                application.setStatus(ApplicationStatus.FAILED_EXTERNAL);
                application.setRejectReason("Taiga integration unavailable: " + ex.getMessage());
                applicationRepository.save(application);
                return;
            }
            throw ex;
        }
    }
}

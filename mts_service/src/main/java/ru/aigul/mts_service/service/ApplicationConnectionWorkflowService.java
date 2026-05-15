package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.exception.ApplicationNotFoundException;
import ru.aigul.mts_service.model.Application;
import ru.aigul.mts_service.model.ApplicationStatus;
import ru.aigul.mts_service.repository.ApplicationRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationConnectionWorkflowService {

    private final ApplicationRepository applicationRepository;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void connectAsynchronously(Long applicationId, String correlationId) {
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (application.getStatus() == ApplicationStatus.CONNECTED) {
            log.info("Application {} already connected", applicationId);
            return;
        }

        if (application.getStatus() != ApplicationStatus.APPROVED) {
            log.info("Skip connection step for application {} with status {}", applicationId, application.getStatus());
            return;
        }

        application.setStatus(ApplicationStatus.CONNECTED);
        applicationRepository.save(application);

        log.info("Application {} connected successfully{}", applicationId,
                correlationId != null ? " correlationId=" + correlationId : "");
    }
}


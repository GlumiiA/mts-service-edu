package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.dto.application.ApplicationDto;
import ru.aigul.mts_service.exception.ApplicationNotFoundException;
import ru.aigul.mts_service.integration.taiga.TaigaStatusChangeEvent;
import ru.aigul.mts_service.integration.taiga.TaigaTaskService;
import ru.aigul.mts_service.model.Application;
import ru.aigul.mts_service.model.ApplicationStatus;
import ru.aigul.mts_service.repository.ApplicationRepository;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaigaApplicationWorkflowService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationApprovalWorkflowService approvalWorkflowService;
    private final TaigaTaskService taigaTaskService;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void handleStatusChanged(TaigaStatusChangeEvent event) {
        if (!taigaTaskService.isWorkflowConfigured()) {
            log.warn("Taiga workflow status ids are not configured; webhook event skipped");
            return;
        }

        Application application = applicationRepository.findByTaigaTaskIdForUpdate(event.userStoryId())
                .orElse(null);
        if (application == null) {
            log.info("Taiga webhook ignored: no application found for taigaTaskId={}", event.userStoryId());
            return;
        }

        if (taigaTaskService.isInProgressStatus(event.statusId())) {
            handleMovedToInProgress(application);
            return;
        }
        if (taigaTaskService.isNewStatus(event.statusId())) {
            handleMovedToNew(application);
            return;
        }
        if (taigaTaskService.isReadyForTestStatus(event.statusId())) {
            handleMovedToReadyForTest(application, event);
            return;
        }
        if (taigaTaskService.isArchivedStatus(event.statusId())) {
            handleMovedToArchived(application);
            return;
        }
        if (taigaTaskService.isDoneStatus(event.statusId())) {
            handleMovedToDone(application);
            return;
        }

        rejectInvalidTransition(application, "Unknown Taiga status: " + event.statusName());
    }

    private void handleMovedToNew(Application application) {
        if (application.getStatus() == ApplicationStatus.PENDING) {
            return;
        }
        rejectInvalidTransition(application, "Application cannot be moved back to New");
    }

    private void handleMovedToInProgress(Application application) {
        if (application.getStatus() == ApplicationStatus.PROCESSING) {
            return;
        }
        if (application.getStatus() != ApplicationStatus.PENDING) {
            rejectInvalidTransition(application, "Only pending applications can be taken into work");
            return;
        }

        approvalWorkflowService.markProcessingFromTaiga(application.getId());
        Application refreshed = applicationRepository.findByIdForUpdate(application.getId())
                .orElseThrow(() -> new ApplicationNotFoundException(application.getId()));
        taigaTaskService.moveApplicationToCurrentStatus(refreshed, "Application is taken into processing");
    }

    private void handleMovedToReadyForTest(Application application, TaigaStatusChangeEvent event) {
        if (application.getStatus() == ApplicationStatus.APPROVED) {
            return;
        }
        if (application.getStatus() != ApplicationStatus.PROCESSING) {
            rejectInvalidTransition(application, "Application must be in progress before approval");
            return;
        }

        ApplicationDto approved = approvalWorkflowService.approveFromTaiga(
                application.getId(),
                event.changedBy(),
                UUID.randomUUID().toString()
        );

        Application refreshed = applicationRepository.findByIdForUpdate(application.getId())
                .orElseThrow(() -> new ApplicationNotFoundException(application.getId()));
        if (approved.getStatus() == ApplicationStatus.REJECTED) {
            taigaTaskService.moveApplicationToArchived(refreshed, "Application rejected automatically: not enough funds");
            return;
        }
        taigaTaskService.moveApplicationToCurrentStatus(refreshed, "Application approved; connection has been requested");
    }

    private void handleMovedToArchived(Application application) {
        if (application.getStatus() == ApplicationStatus.REJECTED) {
            return;
        }
        if (application.getStatus() == ApplicationStatus.PENDING
                || application.getStatus() == ApplicationStatus.PROCESSING) {
            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectReason("Rejected from Taiga");
            applicationRepository.save(application);
            taigaTaskService.moveApplicationToArchived(application, "Application rejected from Taiga");
            return;
        }

        rejectInvalidTransition(application, "Approved or connected applications cannot be archived");
    }

    private void handleMovedToDone(Application application) {
        if (application.getStatus() == ApplicationStatus.CONNECTED) {
            return;
        }
        rejectInvalidTransition(application, "Done is set only by MTS Service after successful connection");
    }

    private void rejectInvalidTransition(Application application, String reason) {
        log.info("Reject Taiga transition for applicationId={}: {}", application.getId(), reason);
        taigaTaskService.moveApplicationToCurrentStatus(application, "Transition rejected: " + reason);
    }
}

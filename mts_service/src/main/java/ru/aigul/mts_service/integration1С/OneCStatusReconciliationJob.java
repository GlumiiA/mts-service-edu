package ru.aigul.mts_service.integration1С;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.aigul.mts_service.model.OneCyncHistory;
import ru.aigul.mts_service.repository.OneCyncHistoryRepository;

import java.time.LocalDateTime;
import java.util.List;


@Component
@Slf4j
public class OneCStatusReconciliationJob implements Job {

    @Autowired
    private OneCyncHistoryRepository syncHistoryRepository;

    @Autowired
    private OneCIntegrationService integrationService;

    private static final int SYNC_TIMEOUT_HOURS = 24;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            log.info("Starting 1C status reconciliation job - node: {}", context.getScheduler().getSchedulerInstanceId());
            
            performReconciliation();
            
            log.info("Status reconciliation job completed successfully");
            
        } catch (Exception e) {
            log.error("Fatal error in 1C status reconciliation job", e);
            throw new JobExecutionException("Reconciliation job failed", e, false);
        }
    }


    private void performReconciliation() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusHours(SYNC_TIMEOUT_HOURS);

        List<OneCyncHistory> stuckRecords = syncHistoryRepository.findBySyncStatus(
            ru.aigul.mts_service.model.OneCIntegrationStatus.PENDING
        );
        
        for (OneCyncHistory record : stuckRecords) {
            if (record.getUpdatedAt().isBefore(timeoutThreshold)) {
                log.warn("Stuck sync record detected: id={}, externalId={}, age={} hours",
                    record.getId(), record.getExternalId(),
                    java.time.temporal.ChronoUnit.HOURS.between(record.getUpdatedAt(), LocalDateTime.now())
                );
                
                try {

                    integrationService.checkStatusWithOneC(record);
                    
                } catch (Exception e) {
                    log.error("Failed to reconcile status for record id={}", record.getId(), e);
                }
            }
        }

        checkActiveSyncsStatus();
    }

    private void checkActiveSyncsStatus() {
        log.debug("Checking status of active syncs with 1C");
        
        // This would typically query 1C to get bulk status updates
        // Implementation depends on 1C API capabilities
        integrationService.performBulkStatusCheck();
    }
}

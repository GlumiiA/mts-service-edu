package ru.aigul.mts_service.service.integration;

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

/**
 * Quartz Job for periodic status reconciliation with 1C system.
 * 
 * Runs periodically to:
 * 1. Check status of all active syncs with 1C
 * 2. Verify that external_id is still valid
 * 3. Identify stuck/abandoned requests
 * 4. Automatically recover from network issues
 * 
 * This job runs only once across cluster thanks to Quartz JDBC JobStore
 * clustering support.
 */
@Component
@Slf4j
public class OneCStatusReconciliationJob implements Job {

    @Autowired
    private OneCyncHistoryRepository syncHistoryRepository;

    @Autowired
    private OneCIntegrationService integrationService;

    // Timeout in hours - if no update in this period, mark for manual review
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

    /**
     * Perform status reconciliation with 1C
     */
    private void performReconciliation() {
        // Find all pending syncs that haven't been updated recently
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusHours(SYNC_TIMEOUT_HOURS);
        
        // Query for stuck records
        List<OneCyncHistory> stuckRecords = syncHistoryRepository.findBySyncStatus(
            ru.aigul.mts_service.model.OneCIntegrationStatus.PENDING
        );
        
        for (OneCyncHistory record : stuckRecords) {
            // Check if record has timed out
            if (record.getUpdatedAt().isBefore(timeoutThreshold)) {
                log.warn("Stuck sync record detected: id={}, externalId={}, age={} hours",
                    record.getId(), record.getExternalId(),
                    java.time.temporal.ChronoUnit.HOURS.between(record.getUpdatedAt(), LocalDateTime.now())
                );
                
                try {
                    // Attempt to check status with 1C
                    integrationService.checkStatusWithOneC(record);
                    
                } catch (Exception e) {
                    log.error("Failed to reconcile status for record id={}", record.getId(), e);
                }
            }
        }
        
        // Also check all active syncs for status updates
        checkActiveSyncsStatus();
    }

    /**
     * Check status of all active syncs with 1C system
     */
    private void checkActiveSyncsStatus() {
        log.debug("Checking status of active syncs with 1C");
        
        // This would typically query 1C to get bulk status updates
        // Implementation depends on 1C API capabilities
        integrationService.performBulkStatusCheck();
    }
}

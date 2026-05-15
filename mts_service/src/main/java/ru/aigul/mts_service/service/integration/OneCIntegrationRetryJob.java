package ru.aigul.mts_service.service.integration;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.aigul.mts_service.model.OneCIntegrationStatus;
import ru.aigul.mts_service.model.OneCyncHistory;
import ru.aigul.mts_service.repository.OneCyncHistoryRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Quartz Job for retrying failed 1C integration requests.
 * 
 * Runs periodically to:
 * 1. Find all pending/retry records ready for next attempt
 * 2. Increment retry count with exponential backoff
 * 3. Re-submit to 1C integration service
 * 4. Move to DLQ if max retries exceeded
 * 
 * This job is executed by Quartz scheduler and runs only once across cluster nodes
 * thanks to JDBC JobStore locking mechanism.
 */
@Component
@Slf4j
public class OneCIntegrationRetryJob implements Job {

    @Autowired
    private OneCyncHistoryRepository syncHistoryRepository;

    @Autowired
    private OneCIntegrationService integrationService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            log.info("Starting 1C integration retry job - node: {}", context.getScheduler().getSchedulerInstanceId());
            
            // Find all sync records ready for retry
            LocalDateTime now = LocalDateTime.now();
            List<OneCyncHistory> pendingRetries = syncHistoryRepository.findPendingAndReadyForRetry(now);
            
            log.info("Found {} records ready for retry", pendingRetries.size());
            
            for (OneCyncHistory syncRecord : pendingRetries) {
                try {
                    retryIntegration(syncRecord);
                } catch (Exception e) {
                    log.error("Error processing retry for sync record id={}", syncRecord.getId(), e);
                }
            }
            
            log.info("Retry job completed successfully");
            
        } catch (Exception e) {
            log.error("Fatal error in 1C integration retry job", e);
            throw new JobExecutionException("Retry job failed", e, false);
        }
    }

    /**
     * Retry individual sync record
     */
    private void retryIntegration(OneCyncHistory syncRecord) {
        if (syncRecord.getRetryCount() >= syncRecord.getMaxRetries()) {
            log.warn("Max retries exceeded for sync record id={}, moving to DLQ", syncRecord.getId());
            syncRecord.setSyncStatus(OneCIntegrationStatus.DLQ);
            syncRecord.setLastError("Max retries exceeded");
            syncHistoryRepository.save(syncRecord);
            
            // TODO: Send to Dead Letter Queue notification
            integrationService.sendToDeadLetterQueue(syncRecord);
            return;
        }

        try {
            // Re-submit to integration service
            integrationService.retrySync(syncRecord);
            
        } catch (Exception e) {
            // Update retry count with exponential backoff
            syncRecord.setRetryCount(syncRecord.getRetryCount() + 1);
            syncRecord.setLastError(e.getMessage());
            syncRecord.setSyncStatus(OneCIntegrationStatus.RETRY);
            
            // Calculate next retry with exponential backoff: 2^retryCount seconds
            long backoffSeconds = (long) Math.pow(2, syncRecord.getRetryCount());
            syncRecord.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
            
            syncHistoryRepository.save(syncRecord);
            log.debug("Scheduled retry for sync record id={} at {}", 
                syncRecord.getId(), syncRecord.getNextRetryAt());
        }
    }
}

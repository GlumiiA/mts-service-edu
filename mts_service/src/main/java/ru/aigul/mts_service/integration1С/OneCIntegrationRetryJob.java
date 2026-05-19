package ru.aigul.mts_service.integration1С;

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

    private void retryIntegration(OneCyncHistory syncRecord) {
        if (syncRecord.getRetryCount() >= syncRecord.getMaxRetries()) {
            log.warn("Max retries exceeded for sync record id={}, moving to DLQ", syncRecord.getId());
            syncRecord.setSyncStatus(OneCIntegrationStatus.DLQ);
            syncRecord.setLastError("Max retries exceeded");
            syncHistoryRepository.save(syncRecord);

            integrationService.sendToDeadLetterQueue(syncRecord);
            return;
        }

        try {

            integrationService.retrySync(syncRecord);
            
        } catch (Exception e) {
            syncRecord.setRetryCount(syncRecord.getRetryCount() + 1);
            syncRecord.setLastError(e.getMessage());
            syncRecord.setSyncStatus(OneCIntegrationStatus.RETRY);
            

            long backoffSeconds = (long) Math.pow(2, syncRecord.getRetryCount());
            syncRecord.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
            
            syncHistoryRepository.save(syncRecord);
            log.debug("Scheduled retry for sync record id={} at {}", 
                syncRecord.getId(), syncRecord.getNextRetryAt());
        }
    }
}

package ru.aigul.mts_service.messaging.inbox;

import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class MessageInboxCleanupJob extends QuartzJobBean {

    @Autowired
    private MessageInboxService messageInboxService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        messageInboxService.cleanupExpired(OffsetDateTime.now());
    }
}

package ru.aigul.mts_service.messaging.outbox;

import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Component
public class OutboxRecoveryJob extends QuartzJobBean {

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        outboxDispatcher.recoverStaleProcessing();
    }
}

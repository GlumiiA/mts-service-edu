package ru.aigul.mts_service.config;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;
import ru.aigul.mts_service.messaging.inbox.MessageInboxCleanupJob;
import ru.aigul.mts_service.messaging.outbox.OutboxDispatcher;

import java.util.Properties;

@Configuration
@Slf4j
public class QuartzSchedulerConfig {

    @Value("${app.quartz.outbox.dispatch-interval-ms:2000}")
    private long outboxDispatchIntervalMs;

    @Value("${app.quartz.outbox.recovery-interval-ms:60000}")
    private long outboxRecoveryIntervalMs;

    @Value("${app.quartz.inbox.cleanup-interval-ms:3600000}")
    private long inboxCleanupIntervalMs;

    @Bean(name = "outboxDispatchJobDetail")
    public MethodInvokingJobDetailFactoryBean outboxDispatchJobDetail(OutboxDispatcher outboxDispatcher) {
        MethodInvokingJobDetailFactoryBean job = new MethodInvokingJobDetailFactoryBean();
        job.setTargetObject(outboxDispatcher);
        job.setTargetMethod("dispatch");
        job.setConcurrent(false);
        return job;
    }

    @Bean(name = "outboxRecoveryJobDetail")
    public MethodInvokingJobDetailFactoryBean outboxRecoveryJobDetail(OutboxDispatcher outboxDispatcher) {
        MethodInvokingJobDetailFactoryBean job = new MethodInvokingJobDetailFactoryBean();
        job.setTargetObject(outboxDispatcher);
        job.setTargetMethod("recoverStaleProcessing");
        job.setConcurrent(false);
        return job;
    }

    @Bean(name = "inboxCleanupJobDetail")
    public MethodInvokingJobDetailFactoryBean inboxCleanupJobDetail(MessageInboxCleanupJob messageInboxCleanupJob) {
        MethodInvokingJobDetailFactoryBean job = new MethodInvokingJobDetailFactoryBean();
        job.setTargetObject(messageInboxCleanupJob);
        job.setTargetMethod("cleanupExpired");
        job.setConcurrent(false);
        return job;
    }

    @Bean
    public SimpleTriggerFactoryBean outboxDispatchTrigger(
            @Qualifier("outboxDispatchJobDetail") JobDetail outboxDispatchJobDetail) {
        SimpleTriggerFactoryBean trigger = new SimpleTriggerFactoryBean();
        trigger.setJobDetail(outboxDispatchJobDetail);
        trigger.setStartDelay(2000L);
        trigger.setRepeatInterval(outboxDispatchIntervalMs);
        trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
        return trigger;
    }

    @Bean
    public SimpleTriggerFactoryBean outboxRecoveryTrigger(
            @Qualifier("outboxRecoveryJobDetail") JobDetail outboxRecoveryJobDetail) {
        SimpleTriggerFactoryBean trigger = new SimpleTriggerFactoryBean();
        trigger.setJobDetail(outboxRecoveryJobDetail);
        trigger.setStartDelay(5000L);
        trigger.setRepeatInterval(outboxRecoveryIntervalMs);
        trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
        return trigger;
    }

    @Bean
    public SimpleTriggerFactoryBean inboxCleanupTrigger(
            @Qualifier("inboxCleanupJobDetail") JobDetail inboxCleanupJobDetail) {
        SimpleTriggerFactoryBean trigger = new SimpleTriggerFactoryBean();
        trigger.setJobDetail(inboxCleanupJobDetail);
        trigger.setStartDelay(10000L);
        trigger.setRepeatInterval(inboxCleanupIntervalMs);
        trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
        return trigger;
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(
            @Qualifier("outboxDispatchTrigger") Trigger outboxDispatchTrigger,
            @Qualifier("outboxRecoveryTrigger") Trigger outboxRecoveryTrigger,
            @Qualifier("inboxCleanupTrigger") Trigger inboxCleanupTrigger) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();

        Properties props = new Properties();

        props.setProperty("org.quartz.scheduler.instanceName", "MtsServiceScheduler");
        props.setProperty("org.quartz.scheduler.instanceId", "AUTO");

        props.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        props.setProperty("org.quartz.threadPool.threadCount", "5");
        props.setProperty("org.quartz.threadPool.threadPriority", "5");

        props.setProperty("org.quartz.scheduler.wrapJobExecutionInUserTransaction", "false");

        factory.setQuartzProperties(props);
        factory.setWaitForJobsToCompleteOnShutdown(true);
        factory.setOverwriteExistingJobs(true);
        factory.setAutoStartup(true);
        factory.setTriggers(outboxDispatchTrigger, outboxRecoveryTrigger, inboxCleanupTrigger);

        log.info("Quartz scheduler configured for outbox/inbox jobs");

        return factory;
    }

    @Bean
    public Scheduler scheduler(SchedulerFactoryBean factory) throws SchedulerException {
        Scheduler scheduler = factory.getObject();
        if (scheduler != null) {
            log.info("Quartz Scheduler initialized: {}", scheduler.getSchedulerInstanceId());
        }
        return scheduler;
    }
}





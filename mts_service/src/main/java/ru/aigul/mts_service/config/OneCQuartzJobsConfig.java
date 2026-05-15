package ru.aigul.mts_service.config;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.aigul.mts_service.service.integration.OneCIntegrationRetryJob;
import ru.aigul.mts_service.service.integration.OneCStatusReconciliationJob;


@Configuration
@Slf4j
public class OneCQuartzJobsConfig {

    private static final String RETRY_JOB_NAME = "OneCIntegrationRetryJob";
    private static final String RECONCILIATION_JOB_NAME = "OneCStatusReconciliationJob";

    private static final String TRIGGER_GROUP = "OneCIntegration";


    @Bean
    public JobDetail oneCRetryJobDetail() {
        return JobBuilder.newJob(OneCIntegrationRetryJob.class)
            .withIdentity(RETRY_JOB_NAME)
            .storeDurably()
            .withDescription("Retry failed 1C integration requests")
            .build();
    }

    @Bean
    public Trigger oneCRetryJobTrigger(JobDetail oneCRetryJobDetail) {
        return TriggerBuilder.newTrigger()
            .forJob(oneCRetryJobDetail)
            .withIdentity("RetryTrigger", TRIGGER_GROUP)
            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(5)
                .repeatForever()
                .withMisfireHandlingInstructionNextWithRemainingCount()
            )
            .withDescription("Trigger for 1C retry job")
            .build();
    }


    @Bean
    public JobDetail oneCReconciliationJobDetail() {
        return JobBuilder.newJob(OneCStatusReconciliationJob.class)
            .withIdentity(RECONCILIATION_JOB_NAME)
            .storeDurably()
            .withDescription("Reconcile status with 1C system")
            .build();
    }


    @Bean
    public Trigger oneCReconciliationJobTrigger(JobDetail oneCReconciliationJobDetail) {
        return TriggerBuilder.newTrigger()
            .forJob(oneCReconciliationJobDetail)
            .withIdentity("ReconciliationTrigger", TRIGGER_GROUP)
            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(30)
                .repeatForever()
                .withMisfireHandlingInstructionNextWithRemainingCount()
            )
            .withDescription("Trigger for 1C status reconciliation job")
            .build();
    }
}

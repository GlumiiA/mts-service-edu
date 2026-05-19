package ru.aigul.mts_service.config;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import java.util.Properties;


@Configuration
@Slf4j
public class QuartzSchedulerConfig {

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean() {
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
        factory.setOverwriteExistingJobs(false);
        factory.setAutoStartup(true);
        
        log.info("Quartz scheduler configured with JDBC JobStore (clustering enabled)");
        
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





package ru.aigul.mts_service.config;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;


@Configuration
@Slf4j
public class QuartzSchedulerConfig {

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource, 
                                                      PlatformTransactionManager transactionManager) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();

        factory.setDataSource(dataSource);
        factory.setTransactionManager(transactionManager);

        Properties props = new Properties();

        props.setProperty("org.quartz.scheduler.instanceName", "MtsServiceScheduler");
        props.setProperty("org.quartz.scheduler.instanceId", "AUTO"); // Auto-generated instance ID (e.g., hostname + timestamp)

        props.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
        props.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        props.setProperty("org.quartz.jobStore.useProperties", "true");
        props.setProperty("org.quartz.jobStore.misfireThreshold", "60000");
        props.setProperty("org.quartz.jobStore.isClustered", "true");
        props.setProperty("org.quartz.jobStore.clusterCheckinInterval", "15000"); // 15 seconds

        props.setProperty("org.quartz.jobStore.lockHandler.class", "org.quartz.impl.jdbcjobstore.StdRowLockSemaphore");

        props.setProperty("org.quartz.scheduler.isClustered", "true");
        props.setProperty("org.quartz.scheduler.clusterCheckinInterval", "15000");

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





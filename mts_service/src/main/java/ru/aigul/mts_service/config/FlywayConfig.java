package ru.aigul.mts_service.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Value("${spring.datasource.xa.url}")
    private String primaryUrl;

    @Value("${spring.datasource.xa.user}")
    private String primaryUser;

    @Value("${spring.datasource.xa.password}")
    private String primaryPassword;

    @Value("${billing.datasource.xa.url}")
    private String billingUrl;

    @Value("${billing.datasource.xa.user}")
    private String billingUser;

    @Value("${billing.datasource.xa.password}")
    private String billingPassword;

    @Bean
    public Flyway primaryFlyway() {
        Flyway flyway = Flyway.configure()
                .dataSource(primaryUrl, primaryUser, primaryPassword)
                .locations("classpath:db/migration/primary")
                .baselineOnMigrate(true)
                .load();
        // Repair checksum mismatches before migration
        flyway.repair();
        flyway.migrate();
        return flyway;
    }

    @Bean
    public Flyway billingFlyway() {
        Flyway flyway = Flyway.configure()
                .dataSource(billingUrl, billingUser, billingPassword)
                .locations("classpath:db/migration/billing")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        return flyway;
    }
}
package ru.aigul.mts_service.config;

import jakarta.transaction.TransactionManager;
import org.postgresql.xa.PGXADataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
    basePackages = "ru.aigul.mts_service.billing.repository",
    entityManagerFactoryRef = "billingEntityManagerFactory",
    transactionManagerRef = "transactionManager"
)
public class BillingDataSourceConfig {

    @Value("${billing.datasource.xa.url}")
    private String url;

    @Value("${billing.datasource.xa.user}")
    private String user;

    @Value("${billing.datasource.xa.password}")
    private String password;

    @Bean
    public DataSource billingDataSource(TransactionManager narayanaTransactionManager) {
        PGXADataSource pgXaDs = new PGXADataSource();
        pgXaDs.setUrl(url);
        pgXaDs.setUser(user);
        pgXaDs.setPassword(password);
        return new XaDataSourceWrapper(pgXaDs, narayanaTransactionManager);
    }

    @Bean
    @DependsOn("billingFlyway")
    public LocalContainerEntityManagerFactoryBean billingEntityManagerFactory(@Qualifier("billingDataSource") DataSource billingDataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(billingDataSource);
        em.setPackagesToScan("ru.aigul.mts_service.billing.model");
        em.setPersistenceUnitName("billingPU");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        em.setJpaPropertyMap(jpaProperties());
        return em;
    }

    private Map<String, Object> jpaProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "validate");
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        props.put("hibernate.transaction.jta.platform",
                "org.hibernate.engine.transaction.jta.platform.internal.JBossStandAloneJtaPlatform");
        props.put("jakarta.persistence.transactionType", "JTA");
        return props;
    }
}
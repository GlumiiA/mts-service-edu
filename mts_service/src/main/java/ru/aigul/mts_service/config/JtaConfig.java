package ru.aigul.mts_service.config;

import com.arjuna.ats.jta.TransactionManager;
import com.arjuna.ats.jta.UserTransaction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.jta.JtaTransactionManager;

@Configuration
@EnableTransactionManagement
public class JtaConfig {

    @Bean
    public jakarta.transaction.TransactionManager narayanaTransactionManager() {
        return TransactionManager.transactionManager();
    }

    @Bean
    public jakarta.transaction.UserTransaction narayanaUserTransaction() {
        return UserTransaction.userTransaction();
    }

    @Bean
    @Primary
    public JtaTransactionManager transactionManager(jakarta.transaction.TransactionManager narayanaTransactionManager,
                                                    jakarta.transaction.UserTransaction narayanaUserTransaction) {
        JtaTransactionManager jtaTm = new JtaTransactionManager();
        jtaTm.setTransactionManager(narayanaTransactionManager);
        jtaTm.setUserTransaction(narayanaUserTransaction);
        jtaTm.setAllowCustomIsolationLevels(true);
        return jtaTm;
    }
}
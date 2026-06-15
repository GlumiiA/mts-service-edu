package ru.aigul.mts_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import ru.aigul.mts_service.integration.jca.TaigaConnectionFactory;
import ru.aigul.mts_service.integration.jca.TaigaManagedConnectionFactory;
import ru.aigul.mts_service.integration.jca.TaigaResourceAdapter;
@Slf4j
@Configuration
public class TaigaJcaConfig {

    @Value("${app.taiga.api-token:}")
    private String apiToken;

    @Value("${app.taiga.project-id:0}")
    private long projectId;

    @Value("${app.taiga.request-timeout-ms:10000}")
    private long requestTimeoutMs;

    @Bean
    public TaigaResourceAdapter taigaResourceAdapter() {
        TaigaResourceAdapter adapter = new TaigaResourceAdapter();
        adapter.setApiToken(apiToken);
        adapter.setProjectId(projectId);
        adapter.setRequestTimeoutMs(requestTimeoutMs);
        log.info("Taiga Resource Adapter configured with projectId={}", projectId);
        return adapter;
    }

    @Bean
    public TaigaManagedConnectionFactory taigaManagedConnectionFactory(
            WebClient taigaWebClient,
            TaigaResourceAdapter taigaResourceAdapter) {

        TaigaManagedConnectionFactory factory = new TaigaManagedConnectionFactory();
        factory.setApiToken(apiToken);
        factory.setProjectId(projectId);
        factory.setRequestTimeoutMs(requestTimeoutMs);
        factory.setWebClient(taigaWebClient);
        factory.setResourceAdapter(taigaResourceAdapter);

        log.info("Taiga Managed Connection Factory configured");
        return factory;
    }

    @Bean
    public TaigaConnectionFactory taigaConnectionFactory(
            TaigaManagedConnectionFactory taigaManagedConnectionFactory) {
        TaigaConnectionFactory factory = new TaigaConnectionFactory(taigaManagedConnectionFactory, null);
        log.info("Taiga Connection Factory configured");
        return factory;
    }
}





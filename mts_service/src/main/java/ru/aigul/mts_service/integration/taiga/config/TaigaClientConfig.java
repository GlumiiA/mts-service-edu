package ru.aigul.mts_service.integration.taiga.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class TaigaClientConfig {

    @Bean
    public WebClient taigaWebClient(
            @Value("${app.taiga.base-url:http://localhost:9000}") String baseUrl,
            @Value("${app.taiga.api-token:}") String apiToken) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (apiToken != null && !apiToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken.trim());
        }

        return builder.build();
    }
}

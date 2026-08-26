package ru.aigul.mts_service.integration.taiga.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Configuration
public class TaigaClientConfig {

    @Bean
    public WebClient taigaWebClient(
            @Value("${app.taiga.base-url:http://localhost:9000}") String baseUrl,
            TaigaAuthTokenManager taigaAuthTokenManager) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(taigaAuthFilter(taigaAuthTokenManager))
                .build();
    }

    /**
     * Injects the current Taiga access token into every request and, if Taiga responds
     * with 401 (e.g. "Token is invalid or expired"), refreshes the token via the
     * refresh token and retries the request once.
     */
    private ExchangeFilterFunction taigaAuthFilter(TaigaAuthTokenManager tokenManager) {
        return (request, next) ->
                Mono.fromCallable(tokenManager::getAccessToken)
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(token -> next.exchange(withBearerToken(request, token)))
                        .flatMap(response -> {
                            if (response.statusCode() == HttpStatus.UNAUTHORIZED) {
                                log.warn("Taiga request unauthorized, refreshing access token and retrying: {}",
                                        request.url());
                                return response.releaseBody()
                                        .then(Mono.fromCallable(tokenManager::refreshAccessToken)
                                                .subscribeOn(Schedulers.boundedElastic()))
                                        .flatMap(newToken -> next.exchange(withBearerToken(request, newToken)));
                            }
                            return Mono.just(response);
                        });
    }

    private ClientRequest withBearerToken(ClientRequest request, String token) {
        return ClientRequest.from(request)
                .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .build();
    }
}

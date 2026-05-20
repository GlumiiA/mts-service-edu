package ru.aigul.mts_service.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.aigul.mts_service.integration.taiga.TaigaWebhookService;

import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/taiga")
public class TaigaWebhookController {

    private final TaigaWebhookService taigaWebhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestHeader(value = "X-TAIGA-WEBHOOK-SIGNATURE", required = false) String signature,
            @RequestBody String payload) {
        log.info("Taiga webhook HTTP request received, signaturePresent={}, payloadSize={}",
                signature != null && !signature.isBlank(),
                payload != null ? payload.length() : 0);
        taigaWebhookService.processWebhook(signature, payload);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}

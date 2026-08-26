package ru.aigul.mts_service.integration.taiga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aigul.mts_service.exception.AccessDeniedException;
import ru.aigul.mts_service.service.TaigaApplicationWorkflowService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaigaWebhookService {

    private static final String HMAC_SHA1 = "HmacSHA1";

    private final ObjectMapper objectMapper;
    private final TaigaApplicationWorkflowService workflowService;

    @Value("${app.taiga.webhook.secret:}")
    private String webhookSecret;

    public void processWebhook(String signatureHeader, String payload) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Taiga webhook secret is not configured");
            throw new IllegalStateException("Taiga webhook secret is not configured");
        }

        if (!isSignatureValid(signatureHeader, payload, webhookSecret)) {
            log.warn("Taiga webhook signature validation failed, signaturePresent={}, payloadSize={}",
                    signatureHeader != null && !signatureHeader.isBlank(),
                    payload != null ? payload.length() : 0);
            throw new AccessDeniedException("Invalid Taiga webhook signature");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Taiga webhook payload");
        }

        String action = text(root, "action");
        String type = text(root, "type");
        String by = text(root.path("by"), "username");

        JsonNode data = root.path("data");
        String ref = text(data, "ref");
        String subject = text(data, "subject");
        String status = text(data.path("status"), "name");

        log.info("Taiga webhook received type={} action={} by={} ref={} status={} subject={}",
                emptyToDash(type), emptyToDash(action), emptyToDash(by), emptyToDash(ref), emptyToDash(status), emptyToDash(subject));

        if (!"userstory".equalsIgnoreCase(type)) {
            return;
        }
        if (!hasStatusChange(root)) {
            log.debug("Taiga webhook ignored: no status change");
            return;
        }

        long userStoryId = data.path("id").asLong(0L);
        long statusId = statusId(data.path("status"));
        if (userStoryId <= 0 || statusId <= 0) {
            log.warn("Taiga webhook ignored: missing userstory id or status id");
            return;
        }

        workflowService.handleStatusChanged(new TaigaStatusChangeEvent(
                userStoryId,
                statusId,
                status,
                by
        ));
    }

    private boolean isSignatureValid(String signatureHeader, String payload, String secret) {
        if (signatureHeader == null || signatureHeader.isBlank() || payload == null) {
            return false;
        }
        String expectedHex = hmacSha1Hex(payload, secret);
        String actual = signatureHeader.trim();
        if (actual.regionMatches(true, 0, "sha1=", 0, 5)) {
            actual = actual.substring(5);
        }
        return constantTimeEquals(expectedHex, actual.toLowerCase());
    }

    private String hmacSha1Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA1);
            mac.init(key);
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot verify Taiga webhook signature", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String asText = value.asText();
        return asText != null && !asText.isBlank() ? asText : null;
    }

    private boolean hasStatusChange(JsonNode root) {
        JsonNode diff = root.path("change").path("diff");
        return diff.has("status") || diff.has("status_id");
    }

    private long statusId(JsonNode statusNode) {
        if (statusNode == null || statusNode.isMissingNode() || statusNode.isNull()) {
            return 0L;
        }
        if (statusNode.isNumber()) {
            return statusNode.asLong();
        }
        return statusNode.path("id").asLong(0L);
    }

    private String emptyToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}

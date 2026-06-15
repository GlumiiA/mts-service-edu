package ru.aigul.mts_service.integration.jca;

import com.fasterxml.jackson.databind.JsonNode;

public interface TaigaConnection {
    JsonNode createUserStory(java.util.Map<String, Object> payload);
    JsonNode getUserStory(long id);
    JsonNode updateUserStory(long id, java.util.Map<String, Object> payload);
    void close();
}

package com.knapp.kisoft.mock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

@Component
public class JsonPayloadMapper {

    private final ObjectMapper objectMapper;

    public JsonPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payload to JSON", e);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize payload from JSON", e);
        }
    }

    public <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize payload from JSON", e);
        }
    }

    /**
     * First non-blank {@code clientNumber} in the payload: top-level field, otherwise nested
     * (e.g. {@code packUnit.clientNumber} or {@code inboundDeliveryReference.clientNumber}).
     */
    public String findClientNumber(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return findClientNumber(objectMapper.valueToTree(payload));
        } catch (Exception e) {
            return null;
        }
    }

    private static String findClientNumber(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            String direct = textOrNull(node.get("clientNumber"));
            if (direct != null) {
                return direct;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                String found = findClientNumber(fields.next().getValue());
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findClientNumber(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (!node.isTextual() && !node.isNumber()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }
}


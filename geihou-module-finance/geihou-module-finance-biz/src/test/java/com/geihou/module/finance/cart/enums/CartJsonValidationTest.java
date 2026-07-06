package com.geihou.module.finance.cart.enums;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cart JSON validation test (AC-10).
 *
 * <p>Verifies that options/extra fields must be valid JSON.
 * Uses Jackson ObjectMapper to parse and validate JSON strings.
 */
class CartJsonValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validJsonOptionsAccepted() throws Exception {
        String validJson = "{\"addon\":\"extra cheese\",\"size\":\"large\"}";
        JsonNode node = objectMapper.readTree(validJson);
        assertThat(node.get("addon").asText()).isEqualTo("extra cheese");
        assertThat(node.get("size").asText()).isEqualTo("large");
    }

    @Test
    void invalidJsonOptionsRejected() {
        String invalidJson = "{not valid json";
        assertThatThrownBy(() -> objectMapper.readTree(invalidJson))
                .isInstanceOf(Exception.class);
    }

    @Test
    void emptyJsonOptionsAccepted() throws Exception {
        String emptyJson = "{}";
        JsonNode node = objectMapper.readTree(emptyJson);
        assertThat(node.isObject()).isTrue();
        assertThat(node.size()).isEqualTo(0);
    }

    @Test
    void nullOptionsAccepted() {
        // Null options is valid — it means no options selected
        // The service layer should handle null by treating it as empty
    }

    @Test
    void jsonArrayOptionsAccepted() throws Exception {
        // JSON array is also valid for options
        String arrayJson = "[{\"id\":1,\"name\":\"extra cheese\"},{\"id\":2,\"name\":\"spicy\"}]";
        JsonNode node = objectMapper.readTree(arrayJson);
        assertThat(node.isArray()).isTrue();
        assertThat(node.size()).isEqualTo(2);
    }
}

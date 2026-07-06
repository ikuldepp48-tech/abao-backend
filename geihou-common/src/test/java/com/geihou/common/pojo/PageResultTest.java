package com.geihou.common.pojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ofShouldCreatePagedResult() {
        PageResult<String> result = PageResult.of(List.of("a", "b"), 2L, 1, 20);

        assertEquals(List.of("a", "b"), result.getList());
        assertEquals(2L, result.getTotal());
        assertEquals(1, result.getPageNo());
        assertEquals(20, result.getPageSize());
    }

    @Test
    void ofShouldRejectNullList() {
        assertThrows(NullPointerException.class, () -> PageResult.of(null, 0L, 1, 20));
    }

    @Test
    void ofShouldRejectNullTotal() {
        assertThrows(NullPointerException.class, () -> PageResult.of(List.of(), null, 1, 20));
    }

    @Test
    void ofShouldRejectNegativeTotal() {
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(List.of(), -1L, 1, 20));
    }

    @Test
    void ofShouldRejectNullPageNo() {
        assertThrows(NullPointerException.class, () -> PageResult.of(List.of(), 0L, null, 20));
    }

    @Test
    void ofShouldRejectPageNoLessThanOne() {
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(List.of(), 0L, 0, 20));
    }

    @Test
    void ofShouldRejectNullPageSize() {
        assertThrows(NullPointerException.class, () -> PageResult.of(List.of(), 0L, 1, null));
    }

    @Test
    void ofShouldRejectPageSizeLessThanOne() {
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(List.of(), 0L, 1, 0));
    }

    @Test
    void emptyShouldReturnEmptyPage() {
        PageResult<String> result = PageResult.empty(2, 10);

        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
        assertEquals(2, result.getPageNo());
        assertEquals(10, result.getPageSize());
    }

    @Test
    void shouldSerializePaginationContract() throws Exception {
        String json = objectMapper.writeValueAsString(PageResult.of(List.of("a"), 1L, 3, 15));
        JsonNode node = objectMapper.readTree(json);

        assertTrue(node.has("list"));
        assertTrue(node.has("total"));
        assertTrue(node.has("pageNo"));
        assertTrue(node.has("pageSize"));
        assertEquals("a", node.get("list").get(0).asText());
        assertEquals(1L, node.get("total").asLong());
        assertEquals(3, node.get("pageNo").asInt());
        assertEquals(15, node.get("pageSize").asInt());
    }

    @Test
    void shouldProtectListFromMutation() {
        List<String> source = new ArrayList<>();
        source.add("a");

        PageResult<String> result = PageResult.of(source, 1L, 1, 10);
        source.add("b");

        assertEquals(List.of("a"), result.getList());
        assertThrows(UnsupportedOperationException.class, () -> result.getList().add("c"));
    }
}

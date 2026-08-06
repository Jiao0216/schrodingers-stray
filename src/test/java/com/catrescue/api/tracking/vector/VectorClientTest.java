package com.catrescue.api.tracking.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorClientTest {

    @Test
    void parseAddResponse_okTrue() throws Exception {
        assertTrue(VectorClient.parseAddResponse("{\"ok\":true,\"id\":\"sighting_1\"}"));
    }

    @Test
    void parseQueryResponse_returnsMatches() throws Exception {
        String json = """
                {"matches":[{"id":"sighting_1","sighting_id":1,"cat_id":10,"distance":0.12,
                "upload_time":"2026-08-06T10:00:00Z","location_approx":"37.33,-121.89","image_url":"u1"}]}
                """;
        List<VectorMatchResult> matches = VectorClient.parseQueryResponse(json);
        assertEquals(1, matches.size());
        assertEquals("sighting_1", matches.get(0).id());
        assertEquals(1L, matches.get(0).sightingId());
        assertEquals(10L, matches.get(0).catId());
        assertEquals(0.12, matches.get(0).distance(), 1e-9);
    }

    @Test
    void parseQueryResponse_nullCatId() throws Exception {
        String json = """
                {"matches":[{"id":"sighting_2","sighting_id":2,"cat_id":null,"distance":0.45,
                "upload_time":"","location_approx":"","image_url":""}]}
                """;
        List<VectorMatchResult> matches = VectorClient.parseQueryResponse(json);
        assertEquals(1, matches.size());
        assertEquals(null, matches.get(0).catId());
    }
}

package com.catrescue.api.service.client;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipEmbeddingClientTest {

    @Test
    void parseEmbeddingResponse_returnsFloatArray() throws Exception {
        String json = """
                {"embedding":[0.1,0.2,0.3],"dimensions":3,"model":"clip-ViT-B-32"}
                """;
        Optional<float[]> parsed = ClipEmbeddingClient.parseEmbeddingResponse(json);
        assertTrue(parsed.isPresent());
        assertEquals(3, parsed.get().length);
        assertEquals(0.1f, parsed.get()[0], 1e-6f);
        assertEquals(0.3f, parsed.get()[2], 1e-6f);
    }

    @Test
    void parseEmbeddingResponse_emptyArray_returnsEmpty() throws Exception {
        Optional<float[]> parsed = ClipEmbeddingClient.parseEmbeddingResponse("{\"embedding\":[]}");
        assertTrue(parsed.isEmpty());
    }
}

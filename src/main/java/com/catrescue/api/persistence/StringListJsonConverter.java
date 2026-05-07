package com.catrescue.api.persistence;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    /**
     * Escape non-ASCII when persisting JSON (e.g. "无法判断" -> "\\u65e0\\u6cd5\\u5224\\u65ad")
     * so legacy MySQL charsets still accept the payload.
     */
    private static final ObjectMapper M = JsonMapper.builder()
            .configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true)
            .build();
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return M.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return M.readValue(dbData, TYPE);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}

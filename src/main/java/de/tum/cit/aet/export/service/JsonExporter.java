package de.tum.cit.aet.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.tum.cit.aet.export.dto.ExportData;

import java.io.IOException;

public final class JsonExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonExporter() {
    }

    public static byte[] export(ExportData data) throws IOException {
        return MAPPER.writeValueAsBytes(data);
    }
}

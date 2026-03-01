package de.tum.cit.aet.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.tum.cit.aet.export.dto.ExportData;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Utility class that serializes {@link ExportData} into pretty-printed JSON.
 * Uses Jackson with {@link JavaTimeModule} for proper date/time formatting.
 */
public final class JsonExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonExporter() {
    }

    /**
     * Serializes the given export data to a JSON byte array.
     *
     * @param data the collected export data
     * @return the JSON content as a byte array
     * @throws UncheckedIOException if serialization fails
     */
    public static byte[] export(ExportData data) {
        try {
            return MAPPER.writeValueAsBytes(data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate JSON export", e);
        }
    }
}

package de.tum.cit.aet.export.web;

import de.tum.cit.aet.export.dto.ExportFormat;
import de.tum.cit.aet.export.service.ExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

/**
 * REST controller exposing export endpoints.
 * Delegates all business logic to {@link ExportService}.
 */
@Slf4j
@RestController
@RequestMapping("api/export")
public class ExportResource {

    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportService exportService;

    public ExportResource(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * Exports analyzed data for an exercise as a downloadable file.
     *
     * @param exerciseId the ID of the exercise to export
     * @param format     the export format ({@code EXCEL} or {@code JSON}), defaults to {@code EXCEL}
     * @param include    data scopes to include, defaults to all (teams, students, chunks, commits)
     * @return the exported file with appropriate content headers
     */
    @GetMapping("/{exerciseId}")
    public ResponseEntity<byte[]> exportData(
            @PathVariable Long exerciseId,
            @RequestParam(defaultValue = "EXCEL") ExportFormat format,
            @RequestParam(defaultValue = "teams,students,chunks,commits") List<String> include) {
        log.info("GET exportData for exerciseId={}, format={}, include={}", exerciseId, format, include);

        try {
            // 1) Delegate data collection and serialization to the service
            Set<String> includeSet = Set.copyOf(include);
            byte[] data = exportService.exportData(exerciseId, format, includeSet);

            // 2) Build response with format-specific headers
            String filename = "export-" + exerciseId + (format == ExportFormat.EXCEL ? ".xlsx" : ".json");
            String contentType = format == ExportFormat.EXCEL ? EXCEL_CONTENT_TYPE : MediaType.APPLICATION_JSON_VALUE;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data);
        } catch (UncheckedIOException e) {
            log.error("Export failed for exerciseId={}", exerciseId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

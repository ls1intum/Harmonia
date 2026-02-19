package de.tum.cit.aet.export.web;

import de.tum.cit.aet.export.dto.ExportFormat;
import de.tum.cit.aet.export.service.ExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("api/export")
@Slf4j
public class ExportResource {

    private final ExportService exportService;

    public ExportResource(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * Export analyzed data for an exercise as a downloadable file.
     *
     * @param exerciseId the exercise ID
     * @param format     the export format (EXCEL, JSON)
     * @param include    data scopes to include
     * @return the file as a byte array response with Content-Disposition header
     */
    @GetMapping("/{exerciseId}")
    public ResponseEntity<byte[]> exportData(
            @PathVariable Long exerciseId,
            @RequestParam(defaultValue = "EXCEL") ExportFormat format,
            @RequestParam(defaultValue = "teams,students") List<String> include) {
        try {
            Set<String> includeSet = Set.copyOf(include);
            byte[] data = exportService.exportData(exerciseId, format, includeSet);

            String filename = "export-" + exerciseId;
            String contentType;
            if (format == ExportFormat.EXCEL) {
                filename += ".xlsx";
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else {
                filename += ".json";
                contentType = "application/json";
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data);
        } catch (Exception e) {
            log.error("Failed to export data for exercise {}", exerciseId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

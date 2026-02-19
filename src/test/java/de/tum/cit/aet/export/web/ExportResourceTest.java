package de.tum.cit.aet.export.web;

import de.tum.cit.aet.export.dto.ExportFormat;
import de.tum.cit.aet.export.service.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportResourceTest {

    @Mock
    private ExportService exportService;

    private ExportResource exportResource;

    @BeforeEach
    void setUp() {
        exportResource = new ExportResource(exportService);
    }

    @Test
    void exportData_csv_returns200WithCsvHeaders() throws IOException {
        when(exportService.exportData(eq(1L), eq(ExportFormat.CSV), any()))
                .thenReturn("csv-content".getBytes());

        ResponseEntity<byte[]> response = exportResource.exportData(1L, ExportFormat.CSV, List.of("teams"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("text/csv", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("export-1.csv"));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("attachment"));
    }

    @Test
    void exportData_excel_returns200WithExcelHeaders() throws IOException {
        when(exportService.exportData(eq(1L), eq(ExportFormat.EXCEL), any()))
                .thenReturn(new byte[]{1, 2, 3});

        ResponseEntity<byte[]> response = exportResource.exportData(1L, ExportFormat.EXCEL, List.of("teams"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("export-1.xlsx"));
    }

    @Test
    void exportData_json_returns200WithJsonHeaders() throws IOException {
        when(exportService.exportData(eq(1L), eq(ExportFormat.JSON), any()))
                .thenReturn("{}".getBytes());

        ResponseEntity<byte[]> response = exportResource.exportData(1L, ExportFormat.JSON, List.of("teams"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/json", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("export-1.json"));
    }

    @Test
    void exportData_serviceThrows_returns500() throws IOException {
        when(exportService.exportData(eq(1L), eq(ExportFormat.CSV), any()))
                .thenThrow(new IOException("Export failed"));

        ResponseEntity<byte[]> response = exportResource.exportData(1L, ExportFormat.CSV, List.of("teams"));

        assertEquals(500, response.getStatusCode().value());
        assertNull(response.getBody());
    }
}

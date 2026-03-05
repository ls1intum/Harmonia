package de.tum.cit.aet.analysis.web;

import de.tum.cit.aet.analysis.dto.CreateEmailMappingRequestDTO;
import de.tum.cit.aet.analysis.dto.DismissEmailRequestDTO;
import de.tum.cit.aet.analysis.dto.EmailMappingDTO;
import de.tum.cit.aet.analysis.service.EmailMappingService;
import de.tum.cit.aet.analysis.service.EmailMappingService.EmailMappingConflictException;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing manual email-to-student mappings
 * and template author configurations.
 */
@RestController
@RequestMapping("/api/exercises/{exerciseId}/email-mappings")
@Slf4j
@RequiredArgsConstructor
public class EmailMappingResource {

    private final EmailMappingService emailMappingService;

    // ================================================================
    //  Email mapping endpoints
    // ================================================================

    @GetMapping
    public ResponseEntity<List<EmailMappingDTO>> getAllMappings(@PathVariable Long exerciseId) {
        log.info("GET getAllMappings for exerciseId={}", exerciseId);
        return ResponseEntity.ok(emailMappingService.getAllMappings(exerciseId));
    }

    @PostMapping
    public ResponseEntity<ClientResponseDTO> createMapping(
            @PathVariable Long exerciseId,
            @RequestBody CreateEmailMappingRequestDTO request) {
        log.info("POST createMapping for exerciseId={}, gitEmail={}, studentId={}",
                exerciseId, request.gitEmail(), request.studentId());
        try {
            return ResponseEntity.ok(emailMappingService.createMapping(exerciseId, request));
        } catch (EmailMappingConflictException e) {
            return ResponseEntity.status(409).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/dismiss")
    public ResponseEntity<ClientResponseDTO> dismissEmail(
            @PathVariable Long exerciseId,
            @RequestBody DismissEmailRequestDTO request) {
        log.info("POST dismissEmail for exerciseId={}, gitEmail={}", exerciseId, request.gitEmail());
        try {
            return emailMappingService.dismissEmail(exerciseId, request)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.noContent().build());
        } catch (EmailMappingConflictException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @DeleteMapping("/{mappingId}")
    public ResponseEntity<ClientResponseDTO> deleteMapping(
            @PathVariable Long exerciseId,
            @PathVariable UUID mappingId) {
        log.info("DELETE deleteMapping for exerciseId={}, mappingId={}", exerciseId, mappingId);
        try {
            return emailMappingService.deleteMapping(exerciseId, mappingId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.noContent().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ================================================================
    //  Template author endpoints
    // ================================================================

    public record TemplateAuthorDTO(
            String templateEmail,
            Boolean autoDetected) {
    }

    @GetMapping("/template-author")
    public ResponseEntity<List<TemplateAuthorDTO>> getTemplateAuthors(@PathVariable Long exerciseId) {
        log.info("GET getTemplateAuthors for exerciseId={}", exerciseId);
        return ResponseEntity.ok(emailMappingService.getTemplateAuthors(exerciseId));
    }

    @PutMapping("/template-author")
    public ResponseEntity<List<ClientResponseDTO>> setTemplateAuthors(
            @PathVariable Long exerciseId,
            @RequestBody List<TemplateAuthorDTO> request) {
        log.info("PUT setTemplateAuthors for exerciseId={}, count={}", exerciseId, request.size());
        return ResponseEntity.ok(emailMappingService.setTemplateAuthors(exerciseId, request));
    }

    @DeleteMapping("/template-author")
    public ResponseEntity<List<ClientResponseDTO>> deleteTemplateAuthors(
            @PathVariable Long exerciseId) {
        log.info("DELETE deleteTemplateAuthors for exerciseId={}", exerciseId);
        return emailMappingService.deleteTemplateAuthors(exerciseId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}

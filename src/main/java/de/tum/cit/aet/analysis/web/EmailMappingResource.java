package de.tum.cit.aet.analysis.web;

import de.tum.cit.aet.analysis.service.EmailMappingService;
import de.tum.cit.aet.analysis.service.EmailMappingService.*;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing manual email-to-student mappings.
 * Delegates all business logic to {@link EmailMappingService}.
 */
@RestController
@RequestMapping("/api/exercises/{exerciseId}/email-mappings")
@Slf4j
@RequiredArgsConstructor
public class EmailMappingResource {

    private final EmailMappingService emailMappingService;

    @GetMapping
    public ResponseEntity<List<EmailMappingDTO>> getAllMappings(@PathVariable Long exerciseId) {
        return ResponseEntity.ok(emailMappingService.getAllMappings(exerciseId));
    }

    @PostMapping
    public ResponseEntity<ClientResponseDTO> createMapping(
            @PathVariable Long exerciseId,
            @RequestBody CreateEmailMappingRequest request) {
        log.info("POST createMapping for exerciseId={}, gitEmail={}, studentId={}",
                exerciseId, request.gitEmail(), request.studentId());
        ClientResponseDTO result = emailMappingService.createMapping(exerciseId, request);
        if (result == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{mappingId}")
    public ResponseEntity<ClientResponseDTO> deleteMapping(
            @PathVariable Long exerciseId,
            @PathVariable UUID mappingId) {
        log.info("DELETE deleteMapping for exerciseId={}, mappingId={}", exerciseId, mappingId);
        return emailMappingService.deleteMapping(exerciseId, mappingId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/template-author")
    public ResponseEntity<TemplateAuthorDTO> getTemplateAuthor(@PathVariable Long exerciseId) {
        return ResponseEntity.ok(emailMappingService.getTemplateAuthor(exerciseId).orElse(null));
    }

    @PutMapping("/template-author")
    public ResponseEntity<List<ClientResponseDTO>> setTemplateAuthor(
            @PathVariable Long exerciseId,
            @RequestBody TemplateAuthorDTO request) {
        log.info("PUT setTemplateAuthor for exerciseId={}, email={}", exerciseId, request.templateEmail());
        return ResponseEntity.ok(emailMappingService.setTemplateAuthor(exerciseId, request.templateEmail()));
    }

    @DeleteMapping("/template-author")
    public ResponseEntity<List<ClientResponseDTO>> deleteTemplateAuthor(
            @PathVariable Long exerciseId) {
        log.info("DELETE deleteTemplateAuthor for exerciseId={}", exerciseId);
        return emailMappingService.deleteTemplateAuthor(exerciseId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}

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

    /**
     * Returns all email mappings for the given exercise.
     *
     * @param exerciseId the exercise ID
     * @return list of email mapping DTOs
     */
    @GetMapping
    public ResponseEntity<List<EmailMappingDTO>> getAllMappings(@PathVariable Long exerciseId) {
        log.info("GET getAllMappings for exerciseId={}", exerciseId);
        return ResponseEntity.ok(emailMappingService.getAllMappings(exerciseId));
    }

    /**
     * Creates a new email mapping and recalculates CQI for the affected team.
     *
     * @param exerciseId the exercise ID
     * @param request    the mapping request with git email, student info and participation ID
     * @return updated client response DTO, 409 if mapping already exists, or 400 if invalid
     */
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

    /**
     * Dismisses an orphan email without assigning it to a student.
     *
     * @param exerciseId the exercise ID
     * @param request    the dismiss request with git email and participation ID
     * @return updated client response DTO, 409 if already mapped, or 204 if no participation found
     */
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

    /**
     * Deletes an email mapping and recalculates CQI for affected teams.
     *
     * @param exerciseId the exercise ID
     * @param mappingId  the mapping ID to delete
     * @return updated client response DTO, or 204 if no chunks were affected
     */
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

    /**
     * Returns all configured template authors for the given exercise.
     *
     * @param exerciseId the exercise ID
     * @return list of template author DTOs
     */
    @GetMapping("/template-author")
    public ResponseEntity<List<TemplateAuthorDTO>> getTemplateAuthors(@PathVariable Long exerciseId) {
        log.info("GET getTemplateAuthors for exerciseId={}", exerciseId);
        return ResponseEntity.ok(emailMappingService.getTemplateAuthors(exerciseId));
    }

    /**
     * Sets or replaces all template authors for an exercise and recalculates CQI.
     *
     * @param exerciseId the exercise ID
     * @param request    list of template author DTOs with emails
     * @return list of updated client response DTOs for all teams
     */
    @PutMapping("/template-author")
    public ResponseEntity<List<ClientResponseDTO>> setTemplateAuthors(
            @PathVariable Long exerciseId,
            @RequestBody List<TemplateAuthorDTO> request) {
        log.info("PUT setTemplateAuthors for exerciseId={}, count={}", exerciseId, request.size());
        return ResponseEntity.ok(emailMappingService.setTemplateAuthors(exerciseId, request));
    }

    /**
     * Removes all template author configurations for an exercise and recalculates CQI.
     *
     * @param exerciseId the exercise ID
     * @return list of updated client response DTOs, or 204 if none configured
     */
    @DeleteMapping("/template-author")
    public ResponseEntity<List<ClientResponseDTO>> deleteTemplateAuthors(
            @PathVariable Long exerciseId) {
        log.info("DELETE deleteTemplateAuthors for exerciseId={}", exerciseId);
        return emailMappingService.deleteTemplateAuthors(exerciseId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}

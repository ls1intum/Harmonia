package de.tum.cit.aet.analysis.web;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.domain.ExerciseEmailMapping;
import de.tum.cit.aet.analysis.domain.ExerciseTemplateAuthor;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.analysis.repository.ExerciseEmailMappingRepository;
import de.tum.cit.aet.analysis.repository.ExerciseTemplateAuthorRepository;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.ai.dto.*;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.FilterSummaryDTO;
import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import de.tum.cit.aet.repositoryProcessing.dto.StudentAnalysisDTO;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * REST controller for managing manual email-to-student mappings.
 * When a mapping is created or removed the CQI is recalculated
 * from the already-persisted LLM scores (no new LLM call).
 */
@RestController
@RequestMapping("/api/exercises/{exerciseId}/email-mappings")
@Slf4j
@RequiredArgsConstructor
public class EmailMappingResource {

    private final ExerciseEmailMappingRepository emailMappingRepository;
    private final ExerciseTemplateAuthorRepository templateAuthorRepository;
    private final AnalyzedChunkRepository analyzedChunkRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final StudentRepository studentRepository;
    private final CQICalculatorService cqiCalculatorService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * DTO for creating a new email mapping.
     */
    public record CreateEmailMappingRequest(
            String gitEmail,
            Long studentId,
            String studentName,
            UUID teamParticipationId) {
    }

    /**
     * DTO returned for each persisted mapping.
     */
    public record EmailMappingDTO(
            UUID id,
            Long exerciseId,
            String gitEmail,
            Long studentId,
            String studentName) {
    }

    /**
     * Returns all email mappings for the given exercise.
     *
     * @param exerciseId the exercise ID
     * @return list of email mapping DTOs
     */
    @GetMapping
    public ResponseEntity<List<EmailMappingDTO>> getAllMappings(@PathVariable Long exerciseId) {
        List<EmailMappingDTO> dtos = emailMappingRepository.findAllByExerciseId(exerciseId)
                .stream()
                .map(m -> new EmailMappingDTO(m.getId(), m.getExerciseId(),
                        m.getGitEmail(), m.getStudentId(), m.getStudentName()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Creates a new email mapping and recalculates CQI for the affected team.
     *
     * @param exerciseId the exercise ID
     * @param request    the mapping request with git email, student info and participation ID
     * @return updated client response DTO with recalculated CQI
     */
    @PostMapping
    @Transactional
    public ResponseEntity<ClientResponseDTO> createMapping(
            @PathVariable Long exerciseId,
            @RequestBody CreateEmailMappingRequest request) {

        log.info("Creating email mapping: {} -> studentId {} for exercise {}",
                request.gitEmail(), request.studentId(), exerciseId);

        // 1. Find the team participation (must exist before we resolve the student ID)
        TeamParticipation participation = teamParticipationRepository
                .findById(request.teamParticipationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "TeamParticipation not found: " + request.teamParticipationId()));

        // 2. Resolve real Artemis student ID by name (frontend may send 0 as placeholder)
        Long resolvedStudentId = request.studentId();
        if (request.studentName() != null) {
            List<Student> students = studentRepository.findAllByTeam(participation);
            resolvedStudentId = students.stream()
                    .filter(s -> request.studentName().equals(s.getName()))
                    .map(Student::getId)
                    .findFirst()
                    .orElse(request.studentId());
        }

        // 3. Save mapping with resolved ID
        ExerciseEmailMapping mapping = new ExerciseEmailMapping(
                exerciseId, request.gitEmail().toLowerCase(Locale.ROOT),
                resolvedStudentId, request.studentName());
        emailMappingRepository.save(mapping);

        // 4. Update chunks: mark matching external chunks as non-external
        List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
        String emailLower = request.gitEmail().toLowerCase(Locale.ROOT);
        for (AnalyzedChunk chunk : chunks) {
            if (Boolean.TRUE.equals(chunk.getIsExternalContributor())
                    && emailLower.equals(chunk.getAuthorEmail() != null
                            ? chunk.getAuthorEmail().toLowerCase(Locale.ROOT) : null)) {
                chunk.setIsExternalContributor(false);
            }
        }
        analyzedChunkRepository.saveAll(chunks);

        // 5. Recalculate CQI from persisted chunks
        recalculateCqi(participation, chunks);

        // 6. Return updated response
        return ResponseEntity.ok(buildResponse(participation));
    }

    /**
     * Deletes an email mapping and recalculates CQI for affected teams.
     *
     * @param exerciseId the exercise ID
     * @param mappingId  the mapping ID to delete
     * @return updated client response DTO, or 204 if no team was affected
     */
    @DeleteMapping("/{mappingId}")
    @Transactional
    public ResponseEntity<ClientResponseDTO> deleteMapping(
            @PathVariable Long exerciseId,
            @PathVariable UUID mappingId) {

        ExerciseEmailMapping mapping = emailMappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + mappingId));

        log.info("Removing email mapping: {} -> studentId {} for exercise {}",
                mapping.getGitEmail(), mapping.getStudentId(), exerciseId);

        emailMappingRepository.delete(mapping);

        // Find all participations for this exercise and update chunks
        List<TeamParticipation> participations = teamParticipationRepository
                .findAllByExerciseId(exerciseId);

        String emailLower = mapping.getGitEmail().toLowerCase(Locale.ROOT);
        ClientResponseDTO lastResponse = null;

        for (TeamParticipation participation : participations) {
            List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
            boolean changed = false;

            // Build set of known student emails (from students + remaining mappings)
            Set<String> knownEmails = buildKnownEmails(participation, exerciseId);

            for (AnalyzedChunk chunk : chunks) {
                String chunkEmail = chunk.getAuthorEmail() != null
                        ? chunk.getAuthorEmail().toLowerCase(Locale.ROOT) : null;
                if (emailLower.equals(chunkEmail)
                        && !knownEmails.contains(chunkEmail)) {
                    chunk.setIsExternalContributor(true);
                    changed = true;
                }
            }

            if (changed) {
                analyzedChunkRepository.saveAll(chunks);
                recalculateCqi(participation, chunks);
                lastResponse = buildResponse(participation);
            }
        }

        if (lastResponse != null) {
            return ResponseEntity.ok(lastResponse);
        }
        return ResponseEntity.noContent().build();
    }

    // ================================================================
    //  Template Author endpoints
    // ================================================================

    /**
     * DTO for the template author response / request.
     */
    public record TemplateAuthorDTO(
            String templateEmail,
            Boolean autoDetected) {
    }

    /**
     * Returns the configured template author for the given exercise.
     *
     * @param exerciseId the exercise ID
     * @return template author DTO, or 404 if not configured
     */
    @GetMapping("/template-author")
    public ResponseEntity<TemplateAuthorDTO> getTemplateAuthor(@PathVariable Long exerciseId) {
        return templateAuthorRepository.findByExerciseId(exerciseId)
                .map(ta -> ResponseEntity.ok(
                        new TemplateAuthorDTO(ta.getTemplateEmail(), ta.getAutoDetected())))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Sets or updates the template author for an exercise.
     * All affected teams' CQI is recalculated from persisted chunks.
     *
     * @param exerciseId the exercise ID
     * @param request    the template author request with email
     * @return list of updated client response DTOs
     */
    @PutMapping("/template-author")
    @Transactional
    public ResponseEntity<List<ClientResponseDTO>> setTemplateAuthor(
            @PathVariable Long exerciseId,
            @RequestBody TemplateAuthorDTO request) {

        String newEmail = request.templateEmail().toLowerCase(Locale.ROOT);
        log.info("Setting template author for exercise {}: {}", exerciseId, newEmail);

        // Load or create template author entity
        ExerciseTemplateAuthor ta = templateAuthorRepository.findByExerciseId(exerciseId)
                .orElse(new ExerciseTemplateAuthor(exerciseId, newEmail, false));
        String oldEmail = ta.getTemplateEmail();
        ta.setTemplateEmail(newEmail);
        ta.setAutoDetected(false); // Manual override
        templateAuthorRepository.save(ta);

        // Recalculate CQI for all teams of this exercise
        List<TeamParticipation> participations = teamParticipationRepository
                .findAllByExerciseId(exerciseId);

        List<ClientResponseDTO> responses = new ArrayList<>();
        for (TeamParticipation participation : participations) {
            List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
            boolean changed = false;

            // Build known emails so we don't accidentally orphan a known student
            Set<String> knownEmails = buildKnownEmails(participation, exerciseId);

            for (AnalyzedChunk chunk : chunks) {
                String chunkEmail = chunk.getAuthorEmail() != null
                        ? chunk.getAuthorEmail().toLowerCase(Locale.ROOT) : null;

                if (oldEmail != null && oldEmail.equalsIgnoreCase(chunkEmail)
                        && !newEmail.equalsIgnoreCase(chunkEmail)) {
                    // Old template email: only mark external if NOT a known student/mapping
                    boolean shouldBeExternal = !knownEmails.contains(
                            chunkEmail != null ? chunkEmail.toLowerCase(Locale.ROOT) : null);
                    chunk.setIsExternalContributor(shouldBeExternal);
                    changed = true;
                }
                if (newEmail.equalsIgnoreCase(chunkEmail)) {
                    // New template email → mark as external (template)
                    chunk.setIsExternalContributor(true);
                    changed = true;
                }
            }

            if (changed) {
                analyzedChunkRepository.saveAll(chunks);
            }
            recalculateCqi(participation, chunks);
            responses.add(buildResponse(participation));
        }

        return ResponseEntity.ok(responses);
    }

    /**
     * Removes the template author configuration for an exercise.
     * Chunks from the old template author become regular orphans again.
     *
     * @param exerciseId the exercise ID
     * @return list of updated client response DTOs
     */
    @DeleteMapping("/template-author")
    @Transactional
    public ResponseEntity<List<ClientResponseDTO>> deleteTemplateAuthor(
            @PathVariable Long exerciseId) {

        ExerciseTemplateAuthor ta = templateAuthorRepository.findByExerciseId(exerciseId)
                .orElse(null);
        if (ta == null) {
            return ResponseEntity.noContent().build();
        }

        String oldEmail = ta.getTemplateEmail().toLowerCase(Locale.ROOT);
        log.info("Removing template author for exercise {}: {}", exerciseId, oldEmail);
        templateAuthorRepository.delete(ta);

        // Recalculate CQI for all teams — old template chunks stay as external/orphan
        // unless they match a student or email mapping
        List<TeamParticipation> participations = teamParticipationRepository
                .findAllByExerciseId(exerciseId);

        List<ClientResponseDTO> responses = new ArrayList<>();
        for (TeamParticipation participation : participations) {
            List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);

            // Unmark chunks whose old template email is actually a known student/mapping
            Set<String> knownEmails = buildKnownEmails(participation, exerciseId);
            boolean changed = false;
            for (AnalyzedChunk chunk : chunks) {
                String chunkEmail = chunk.getAuthorEmail() != null
                        ? chunk.getAuthorEmail().toLowerCase(Locale.ROOT) : null;
                if (oldEmail.equals(chunkEmail)
                        && Boolean.TRUE.equals(chunk.getIsExternalContributor())
                        && knownEmails.contains(chunkEmail)) {
                    chunk.setIsExternalContributor(false);
                    changed = true;
                }
            }
            if (changed) {
                analyzedChunkRepository.saveAll(chunks);
            }

            recalculateCqi(participation, chunks);
            responses.add(buildResponse(participation));
        }

        return ResponseEntity.ok(responses);
    }

    /**
     * Builds the set of emails that are considered "known" for a participation.
     * Includes student emails and remaining exercise email mappings.
     */
    private Set<String> buildKnownEmails(TeamParticipation participation, Long exerciseId) {
        Set<String> known = new HashSet<>();
        List<Student> students = studentRepository.findAllByTeam(participation);
        for (Student s : students) {
            if (s.getEmail() != null) {
                known.add(s.getEmail().toLowerCase(Locale.ROOT));
            }
        }
        for (ExerciseEmailMapping m : emailMappingRepository.findAllByExerciseId(exerciseId)) {
            known.add(m.getGitEmail().toLowerCase(Locale.ROOT));
        }
        return known;
    }

    /**
     * Recalculates CQI from already-persisted AnalyzedChunk data.
     * Only non-external chunks are included in the CQI calculation.
     */
    private void recalculateCqi(TeamParticipation participation,
            List<AnalyzedChunk> allChunks) {

        List<Student> students = studentRepository.findAllByTeam(participation);
        int teamSize = students.size();

        // Collect non-external chunks for CQI
        List<AnalyzedChunk> teamChunks = allChunks.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsExternalContributor()))
                .toList();

        // Count remaining external chunks as orphans
        long orphanCount = allChunks.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsExternalContributor()))
                .count();
        participation.setOrphanCommitCount((int) orphanCount);

        if (teamChunks.isEmpty() || teamSize <= 1) {
            participation.setCqi(0.0);
            teamParticipationRepository.save(participation);
            return;
        }

        // Build email -> studentId map from students + mappings
        Map<String, Long> emailToStudentId = new HashMap<>();
        for (Student s : students) {
            if (s.getEmail() != null && s.getId() != null) {
                emailToStudentId.put(s.getEmail().toLowerCase(Locale.ROOT), s.getId());
            }
        }
        List<ExerciseEmailMapping> mappings = emailMappingRepository
                .findAllByExerciseId(participation.getExerciseId());
        for (ExerciseEmailMapping m : mappings) {
            emailToStudentId.put(m.getGitEmail().toLowerCase(Locale.ROOT), m.getStudentId());
        }

        // Reconstruct RatedChunks from DB data
        List<CQICalculatorService.RatedChunk> ratedChunks = teamChunks.stream()
                .map(chunk -> {
                    String chunkEmail = chunk.getAuthorEmail() != null
                            ? chunk.getAuthorEmail().toLowerCase(Locale.ROOT) : "";
                    Long authorId = emailToStudentId.getOrDefault(chunkEmail, -1L);

                    CommitChunkDTO chunkDTO = CommitChunkDTO.single(
                            chunk.getCommitShas() != null ? chunk.getCommitShas().split(",")[0] : "",
                            authorId,
                            chunk.getAuthorEmail(),
                            "", // commit message not needed for CQI
                            chunk.getTimestamp(),
                            List.of(), // files not needed
                            "", // diff not needed
                            chunk.getLinesChanged() != null ? chunk.getLinesChanged() : 0,
                            0);

                    CommitLabel label;
                    try {
                        label = CommitLabel.valueOf(chunk.getClassification());
                    } catch (Exception e) {
                        label = CommitLabel.TRIVIAL;
                    }

                    EffortRatingDTO rating = new EffortRatingDTO(
                            chunk.getEffortScore() != null ? chunk.getEffortScore() : 0.0,
                            chunk.getComplexity() != null ? chunk.getComplexity() : 0.0,
                            chunk.getNovelty() != null ? chunk.getNovelty() : 0.0,
                            label,
                            chunk.getConfidence() != null ? chunk.getConfidence() : 0.0,
                            chunk.getReasoning(),
                            Boolean.TRUE.equals(chunk.getIsError()),
                            chunk.getErrorMessage());

                    return new CQICalculatorService.RatedChunk(chunkDTO, rating);
                })
                .toList();

        // Calculate date range
        LocalDateTime projectStart = teamChunks.stream()
                .map(AnalyzedChunk::getTimestamp)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusDays(30));

        LocalDateTime projectEnd = teamChunks.stream()
                .map(AnalyzedChunk::getTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        CQIResultDTO cqiResult = cqiCalculatorService.calculate(
                ratedChunks, teamSize, projectStart, projectEnd,
                FilterSummaryDTO.empty(), participation.getName());

        // Update participation — preserve ownership spread from original analysis
        // because we don't have the file list for recalculation
        Double originalOwnership = participation.getCqiOwnershipSpread();
        participation.setCqi(cqiResult.cqi());
        if (cqiResult.components() != null) {
            participation.setCqiEffortBalance(cqiResult.components().effortBalance());
            participation.setCqiLocBalance(cqiResult.components().locBalance());
            participation.setCqiTemporalSpread(cqiResult.components().temporalSpread());
            if (originalOwnership != null) {
                participation.setCqiOwnershipSpread(originalOwnership);
            }
            participation.setCqiBaseScore(cqiResult.baseScore());
            participation.setCqiPenaltyMultiplier(cqiResult.penaltyMultiplier());
            if (cqiResult.penalties() != null) {
                participation.setCqiPenalties(serializePenalties(cqiResult.penalties()));
            }
        }
        teamParticipationRepository.save(participation);

        log.info("Recalculated CQI for team {}: {} (from {} team chunks, {} orphan)",
                participation.getName(), cqiResult.cqi(), teamChunks.size(), orphanCount);
    }

    private ClientResponseDTO buildResponse(TeamParticipation participation) {
        List<Student> students = studentRepository.findAllByTeam(participation);
        List<StudentAnalysisDTO> studentDTOs = students.stream()
                .map(s -> new StudentAnalysisDTO(s.getName(), s.getCommitCount(),
                        s.getLinesAdded(), s.getLinesDeleted(), s.getLinesChanged()))
                .toList();

        return new ClientResponseDTO(
                participation.getTutor() != null ? participation.getTutor().getName() : "Unassigned",
                participation.getTeam(),
                participation.getName(),
                participation.getSubmissionCount(),
                studentDTOs,
                participation.getCqi(),
                participation.getIsSuspicious(),
                participation.getAnalysisStatus(),
                null, // CQI details will be loaded by regular getData endpoint
                loadAnalyzedChunkDTOs(participation),
                null, // orphan commits not persisted
                readTeamTokenTotals(participation),
                participation.getOrphanCommitCount());
    }

    private List<AnalyzedChunkDTO> loadAnalyzedChunkDTOs(TeamParticipation participation) {
        List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
        if (chunks.isEmpty()) {
            return null;
        }
        return chunks.stream()
                .map(chunk -> new AnalyzedChunkDTO(
                        chunk.getChunkIdentifier(),
                        chunk.getAuthorEmail(),
                        chunk.getAuthorName(),
                        chunk.getClassification(),
                        chunk.getEffortScore() != null ? chunk.getEffortScore() : 0.0,
                        chunk.getComplexity() != null ? chunk.getComplexity() : 0.0,
                        chunk.getNovelty() != null ? chunk.getNovelty() : 0.0,
                        chunk.getConfidence() != null ? chunk.getConfidence() : 0.0,
                        chunk.getReasoning(),
                        List.of(chunk.getCommitShas().split(",")),
                        parseCommitMessages(chunk.getCommitMessages()),
                        chunk.getTimestamp(),
                        chunk.getLinesChanged() != null ? chunk.getLinesChanged() : 0,
                        Boolean.TRUE.equals(chunk.getIsBundled()),
                        chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0,
                        chunk.getTotalChunks() != null ? chunk.getTotalChunks() : 1,
                        Boolean.TRUE.equals(chunk.getIsError()),
                        chunk.getErrorMessage(),
                        Boolean.TRUE.equals(chunk.getIsExternalContributor()),
                        new LlmTokenUsage(
                                chunk.getLlmModel() != null ? chunk.getLlmModel() : "unknown",
                                chunk.getLlmPromptTokens() != null ? chunk.getLlmPromptTokens() : 0L,
                                chunk.getLlmCompletionTokens() != null ? chunk.getLlmCompletionTokens() : 0L,
                                chunk.getLlmTotalTokens() != null
                                        ? chunk.getLlmTotalTokens()
                                        : (chunk.getLlmPromptTokens() != null ? chunk.getLlmPromptTokens() : 0L)
                                        + (chunk.getLlmCompletionTokens() != null
                                                ? chunk.getLlmCompletionTokens() : 0L),
                                Boolean.TRUE.equals(chunk.getLlmUsageAvailable()))))
                .toList();
    }

    private LlmTokenTotals readTeamTokenTotals(TeamParticipation p) {
        if (p.getLlmCalls() == null) {
            return null;
        }
        return new LlmTokenTotals(
                p.getLlmCalls() != null ? p.getLlmCalls() : 0L,
                p.getLlmCallsWithUsage() != null ? p.getLlmCallsWithUsage() : 0L,
                p.getLlmPromptTokens() != null ? p.getLlmPromptTokens() : 0L,
                p.getLlmCompletionTokens() != null ? p.getLlmCompletionTokens() : 0L,
                p.getLlmTotalTokens() != null ? p.getLlmTotalTokens() : 0L);
    }

    @SuppressWarnings("unchecked")
    private List<String> parseCommitMessages(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return List.of();
            }
            return OBJECT_MAPPER.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String serializePenalties(List<?> penalties) {
        try {
            return OBJECT_MAPPER.writeValueAsString(penalties);
        } catch (Exception e) {
            return "[]";
        }
    }
}

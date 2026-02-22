package de.tum.cit.aet.analysis.web;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.domain.ExerciseEmailMapping;
import de.tum.cit.aet.analysis.domain.ExerciseTemplateAuthor;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.analysis.repository.ExerciseEmailMappingRepository;
import de.tum.cit.aet.analysis.repository.ExerciseTemplateAuthorRepository;
import de.tum.cit.aet.analysis.service.cqi.CqiRecalculationService;
import de.tum.cit.aet.ai.dto.*;
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
    private final CqiRecalculationService cqiRecalculationService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * DTO for creating a new email mapping.
     */
    public record CreateEmailMappingRequest(
            String gitEmail,
            Long studentId,
            String studentName,
            Long teamParticipationId) {
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
                .findByExerciseIdAndTeam(exerciseId, request.teamParticipationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "TeamParticipation not found for exercise " + exerciseId
                                + " and team " + request.teamParticipationId()));

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
        List<AnalyzedChunk> remappedChunks = new ArrayList<>();
        for (AnalyzedChunk chunk : chunks) {
            if (Boolean.TRUE.equals(chunk.getIsExternalContributor())
                    && emailLower.equals(chunk.getAuthorEmail() != null
                            ? chunk.getAuthorEmail().toLowerCase(Locale.ROOT) : null)) {
                chunk.setIsExternalContributor(false);
                chunk.setAuthorName(request.studentName());
                remappedChunks.add(chunk);
            }
        }
        analyzedChunkRepository.saveAll(chunks);

        // 5. Update target student's commit/line stats with the remapped chunks
        if (!remappedChunks.isEmpty()) {
            addChunkStatsToStudent(participation, request.studentName(), remappedChunks);
        }

        // 6. Recalculate CQI from persisted chunks
        cqiRecalculationService.recalculateFromChunks(participation, chunks);

        // 7. Return updated response
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
            List<AnalyzedChunk> orphanedChunks = new ArrayList<>();

            // Build set of known student emails (from students + remaining mappings)
            Set<String> knownEmails = buildKnownEmails(participation, exerciseId);

            for (AnalyzedChunk chunk : chunks) {
                String chunkEmail = chunk.getAuthorEmail() != null
                        ? chunk.getAuthorEmail().toLowerCase(Locale.ROOT) : null;
                if (emailLower.equals(chunkEmail)
                        && !knownEmails.contains(chunkEmail)) {
                    chunk.setIsExternalContributor(true);
                    chunk.setAuthorName(chunk.getAuthorEmail());
                    orphanedChunks.add(chunk);
                }
            }

            if (!orphanedChunks.isEmpty()) {
                analyzedChunkRepository.saveAll(chunks);
                subtractChunkStatsFromStudent(participation, mapping.getStudentName(), orphanedChunks);
                cqiRecalculationService.recalculateFromChunks(participation, chunks);
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
            cqiRecalculationService.recalculateFromChunks(participation, chunks);
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

            cqiRecalculationService.recalculateFromChunks(participation, chunks);
            responses.add(buildResponse(participation));
        }

        return ResponseEntity.ok(responses);
    }

    /**
     * Adds the commit/line stats from the given chunks to the named student.
     * The linesAdded/linesDeleted split is distributed proportionally based on
     * the student's existing ratio, since chunks only store total linesChanged.
     */
    private void addChunkStatsToStudent(TeamParticipation participation, String studentName,
                                        List<AnalyzedChunk> addedChunks) {
        ChunkStatsDelta delta = computeChunkStats(addedChunks);
        if (delta.commits == 0 && delta.linesChanged == 0) {
            return;
        }

        studentRepository.findAllByTeam(participation).stream()
                .filter(s -> studentName.equals(s.getName()))
                .findFirst()
                .ifPresent(student -> {
                    student.setCommitCount(safe(student.getCommitCount()) + delta.commits);
                    student.setLinesChanged(safe(student.getLinesChanged()) + delta.linesChanged);
                    applyLinesSplit(student, delta.linesChanged, true);
                    studentRepository.save(student);
                });
    }

    /**
     * Subtracts the commit/line stats of the given chunks from the named student.
     */
    private void subtractChunkStatsFromStudent(TeamParticipation participation, String studentName,
                                               List<AnalyzedChunk> removedChunks) {
        ChunkStatsDelta delta = computeChunkStats(removedChunks);
        if (delta.commits == 0 && delta.linesChanged == 0) {
            return;
        }

        studentRepository.findAllByTeam(participation).stream()
                .filter(s -> studentName.equals(s.getName()))
                .findFirst()
                .ifPresent(student -> {
                    student.setCommitCount(Math.max(0, safe(student.getCommitCount()) - delta.commits));
                    student.setLinesChanged(Math.max(0, safe(student.getLinesChanged()) - delta.linesChanged));
                    applyLinesSplit(student, delta.linesChanged, false);
                    studentRepository.save(student);
                });
    }

    private record ChunkStatsDelta(int commits, int linesChanged) {}

    private ChunkStatsDelta computeChunkStats(List<AnalyzedChunk> chunks) {
        int totalCommits = 0;
        int totalLines = 0;
        for (AnalyzedChunk chunk : chunks) {
            if (chunk.getCommitShas() != null && !chunk.getCommitShas().isEmpty()) {
                totalCommits += chunk.getCommitShas().split(",").length;
            }
            totalLines += chunk.getLinesChanged() != null ? chunk.getLinesChanged() : 0;
        }
        return new ChunkStatsDelta(totalCommits, totalLines);
    }

    /**
     * Distributes a linesChanged delta into linesAdded/linesDeleted
     * using the student's existing ratio.
     */
    private void applyLinesSplit(Student student, int deltaLines, boolean add) {
        int oldAdded = safe(student.getLinesAdded());
        int oldDeleted = safe(student.getLinesDeleted());
        int oldTotal = oldAdded + oldDeleted;

        int deltaAdded;
        int deltaDeleted;
        if (oldTotal > 0) {
            deltaAdded = (int) Math.round(deltaLines * ((double) oldAdded / oldTotal));
            deltaDeleted = deltaLines - deltaAdded;
        } else {
            deltaAdded = deltaLines;
            deltaDeleted = 0;
        }

        if (add) {
            student.setLinesAdded(oldAdded + deltaAdded);
            student.setLinesDeleted(oldDeleted + deltaDeleted);
        } else {
            student.setLinesAdded(Math.max(0, oldAdded - deltaAdded));
            student.setLinesDeleted(Math.max(0, oldDeleted - deltaDeleted));
        }
    }

    private static int safe(Integer value) {
        return value != null ? value : 0;
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

}

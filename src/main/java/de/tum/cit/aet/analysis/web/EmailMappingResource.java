package de.tum.cit.aet.analysis.web;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.domain.ExerciseEmailMapping;
import de.tum.cit.aet.analysis.domain.ExerciseTemplateAuthor;
import de.tum.cit.aet.analysis.dto.CreateEmailMappingRequestDTO;
import de.tum.cit.aet.analysis.dto.DismissEmailRequestDTO;
import de.tum.cit.aet.analysis.dto.EmailMappingDTO;
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
                        m.getGitEmail(), m.getStudentId(), m.getStudentName(),
                        m.getIsDismissed()))
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
            @RequestBody CreateEmailMappingRequestDTO request) {

        log.info("POST createMapping for exerciseId={}, gitEmail={}, studentId={}",
                exerciseId, request.gitEmail(), request.studentId());

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
        if (resolvedStudentId == null || resolvedStudentId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        // 3. Check for existing mapping with the same email
        String normalizedEmail = request.gitEmail().toLowerCase(Locale.ROOT);
        if (emailMappingRepository.existsByExerciseIdAndGitEmail(exerciseId, normalizedEmail)) {
            return ResponseEntity.status(409).build();
        }

        // 4. Save mapping with resolved ID
        ExerciseEmailMapping mapping = new ExerciseEmailMapping(
                exerciseId, normalizedEmail,
                resolvedStudentId, request.studentName());
        emailMappingRepository.save(mapping);

        // 5. Update chunks: mark matching external chunks as non-external
        List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
        List<AnalyzedChunk> remappedChunks = new ArrayList<>();
        for (AnalyzedChunk chunk : chunks) {
            if (isExternalChunkForEmail(chunk, normalizedEmail)) {
                chunk.setIsExternalContributor(false);
                chunk.setAuthorName(request.studentName());
                remappedChunks.add(chunk);
            }
        }
        analyzedChunkRepository.saveAll(remappedChunks);

        // 6. Update target student's commit/line stats with the remapped chunks
        if (!remappedChunks.isEmpty()) {
            addChunkStatsToStudent(participation, request.studentName(), remappedChunks);
        }

        // 7. Recalculate CQI from persisted chunks
        cqiRecalculationService.recalculateFromChunks(participation, chunks);
        // Recompute and persist orphan commit count so clients see updated unmatched counts
        recomputeAndPersistOrphanCount(participation, exerciseId);

        // 8. Return updated response
        return ResponseEntity.ok(buildResponse(participation));
    }

    /**
     * Dismisses an orphan email without assigning it to a student.
     * Chunks are NOT mutated — the client uses the dismissed mapping
     * to display them in a separate "Dismissed" section.
     *
     * @param exerciseId the exercise ID
     * @param request    the dismiss request with git email and participation ID
     * @return updated client response DTO
     */
    @PostMapping("/dismiss")
    @Transactional
    public ResponseEntity<ClientResponseDTO> dismissEmail(
            @PathVariable Long exerciseId,
            @RequestBody DismissEmailRequestDTO request) {

        log.info("POST dismissEmail for exerciseId={}, gitEmail={}", exerciseId, request.gitEmail());

        // 1. Normalize and check for duplicates
        String normalizedEmail = request.gitEmail().toLowerCase(Locale.ROOT);
        if (emailMappingRepository.existsByExerciseIdAndGitEmail(exerciseId, normalizedEmail)) {
            return ResponseEntity.status(409).build();
        }

        // 2. Save dismissed mapping (no student)
        ExerciseEmailMapping mapping = new ExerciseEmailMapping(exerciseId, normalizedEmail, true);
        emailMappingRepository.save(mapping);

        // 3. Recalculate so orphanCommitCount excludes dismissed emails
        TeamParticipation participation = teamParticipationRepository
                .findByExerciseIdAndTeam(exerciseId, request.teamParticipationId())
                .orElse(null);

        if (participation != null) {
            List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
            cqiRecalculationService.recalculateFromChunks(participation, chunks);
            recomputeAndPersistOrphanCount(participation, exerciseId);
            return ResponseEntity.ok(buildResponse(participation));
        }
        return ResponseEntity.noContent().build();
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

        if (!mapping.getExerciseId().equals(exerciseId)) {
            return ResponseEntity.notFound().build();
        }

        log.info("DELETE deleteMapping for exerciseId={}, gitEmail={}, studentId={}",
                exerciseId, mapping.getGitEmail(), mapping.getStudentId());

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
                analyzedChunkRepository.saveAll(orphanedChunks);
                if (!Boolean.TRUE.equals(mapping.getIsDismissed())) {
                    subtractChunkStatsFromStudent(participation, mapping.getStudentName(), orphanedChunks);
                }
                cqiRecalculationService.recalculateFromChunks(participation, chunks);
                recomputeAndPersistOrphanCount(participation, exerciseId);
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
     * Returns all configured template authors for the given exercise.
     *
     * @param exerciseId the exercise ID
     * @return list of template author DTOs (empty list if none configured)
     */
    @GetMapping("/template-author")
    public ResponseEntity<List<TemplateAuthorDTO>> getTemplateAuthors(@PathVariable Long exerciseId) {
        List<TemplateAuthorDTO> dtos = templateAuthorRepository.findByExerciseId(exerciseId)
                .stream()
                .map(ta -> new TemplateAuthorDTO(ta.getTemplateEmail(), ta.getAutoDetected()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Sets or replaces all template authors for an exercise.
     * All affected teams' CQI is recalculated from persisted chunks.
     *
     * @param exerciseId the exercise ID
     * @param request    list of template author DTOs with emails
     * @return list of updated client response DTOs
     */
    @PutMapping("/template-author")
    @Transactional
    public ResponseEntity<List<ClientResponseDTO>> setTemplateAuthors(
            @PathVariable Long exerciseId,
            @RequestBody List<TemplateAuthorDTO> request) {

        // Collect old emails
        Set<String> oldEmails = templateAuthorRepository.findByExerciseId(exerciseId)
                .stream()
                .map(ta -> ta.getTemplateEmail().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        // Delete all existing, flush to ensure DB state is updated, then save new (deduplicated, lowercase)
        templateAuthorRepository.deleteByExerciseId(exerciseId);
        // Ensure the bulk delete is executed in the database before inserting new records to avoid unique constraint races
        templateAuthorRepository.flush();
        Set<String> newEmails = new LinkedHashSet<>();
        for (TemplateAuthorDTO dto : request) {
            String email = dto.templateEmail().toLowerCase(Locale.ROOT);
            if (newEmails.add(email)) {
                templateAuthorRepository.save(
                        new ExerciseTemplateAuthor(exerciseId, email, false));
            }
        }

        log.info("PUT setTemplateAuthors for exerciseId={}, emails={}", exerciseId, newEmails);

        // Recalculate CQI for all teams of this exercise
        List<TeamParticipation> participations = teamParticipationRepository
                .findAllByExerciseId(exerciseId);

        List<ClientResponseDTO> responses = new ArrayList<>();
        for (TeamParticipation participation : participations) {
            List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
            boolean changed = false;

            Set<String> knownEmails = buildKnownEmails(participation, exerciseId);

            for (AnalyzedChunk chunk : chunks) {
                String chunkEmail = chunk.getAuthorEmail() != null
                        ? chunk.getAuthorEmail().toLowerCase(Locale.ROOT) : null;

                if (chunkEmail != null && oldEmails.contains(chunkEmail)
                        && !newEmails.contains(chunkEmail)) {
                    // Was template, no longer template
                    boolean shouldBeExternal = !knownEmails.contains(chunkEmail);
                    chunk.setIsExternalContributor(shouldBeExternal);
                    changed = true;
                }
                if (chunkEmail != null && newEmails.contains(chunkEmail)) {
                    // New template email → mark as external (template)
                    chunk.setIsExternalContributor(true);
                    changed = true;
                }
            }

            if (changed) {
                analyzedChunkRepository.saveAll(chunks);
            }
            cqiRecalculationService.recalculateFromChunks(participation, chunks);
            recomputeAndPersistOrphanCount(participation, exerciseId);
            responses.add(buildResponse(participation));
        }

        return ResponseEntity.ok(responses);
    }

    /**
     * Removes all template author configurations for an exercise.
     * Chunks from the old template authors become regular orphans again.
     *
     * @param exerciseId the exercise ID
     * @return list of updated client response DTOs
     */
    @DeleteMapping("/template-author")
    @Transactional
    public ResponseEntity<List<ClientResponseDTO>> deleteTemplateAuthors(
            @PathVariable Long exerciseId) {

        List<ExerciseTemplateAuthor> existing = templateAuthorRepository.findByExerciseId(exerciseId);
        if (existing.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        Set<String> oldEmails = existing.stream()
                .map(ta -> ta.getTemplateEmail().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        log.info("DELETE deleteTemplateAuthors for exerciseId={}, emails={}", exerciseId, oldEmails);
        templateAuthorRepository.deleteByExerciseId(exerciseId);
        // Make sure deletions hit the DB before recalculations / further processing
        templateAuthorRepository.flush();

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
                if (chunkEmail != null && oldEmails.contains(chunkEmail)
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
            recomputeAndPersistOrphanCount(participation, exerciseId);
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

    private void applyLinesSplit(Student student, int deltaLines, boolean add) {
        CqiRecalculationService.applyLinesSplit(student, deltaLines, add);
    }

    private static int safe(Integer value) {
        return value != null ? value : 0;
    }

    private static boolean isExternalChunkForEmail(AnalyzedChunk chunk, String normalizedEmail) {
        return Boolean.TRUE.equals(chunk.getIsExternalContributor())
                && normalizedEmail.equals(chunk.getAuthorEmail() != null
                        ? chunk.getAuthorEmail().toLowerCase(Locale.ROOT) : null);
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
                participation.getParticipation(),
                participation.getName(),
                participation.getShortName(),
                participation.getSubmissionCount(),
                studentDTOs,
                participation.getCqi(),
                participation.getIsSuspicious(),
                participation.getAnalysisStatus(),
                null, // CQI details will be loaded by regular getData endpoint
                loadAnalyzedChunkDTOs(participation),
                null, // orphan commits not persisted
                readTeamTokenTotals(participation),
                participation.getOrphanCommitCount(),
                participation.getIsFailed(),
                participation.getIsReviewed());
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
                        new LlmTokenUsageDTO(
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

    private LlmTokenTotalsDTO readTeamTokenTotals(TeamParticipation p) {
        if (p == null || p.getLlmCalls() == null) {
            return null;
        }
        return new LlmTokenTotalsDTO(
                p.getLlmCalls(),
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

    /**
     * Recomputes the orphan commit count for the participation by counting
     * commits from chunks still marked as external contributors and persists
     * the updated TeamParticipation so clients see the change immediately.
     */
    private void recomputeAndPersistOrphanCount(TeamParticipation participation, Long exerciseId) {
        List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
        Set<String> templateAuthorEmails = templateAuthorRepository.findByExerciseId(exerciseId)
                .stream().map(ta -> ta.getTemplateEmail().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        int remainingOrphanCommits = 0;
        for (AnalyzedChunk c : chunks) {
            if (Boolean.TRUE.equals(c.getIsExternalContributor())) {
                String chunkEmail = c.getAuthorEmail() != null ? c.getAuthorEmail().toLowerCase(Locale.ROOT) : null;
                if (chunkEmail != null && templateAuthorEmails.contains(chunkEmail)) {
                    continue; // do not count template authors as unmatched
                }
                if (c.getCommitShas() != null && !c.getCommitShas().isEmpty()) {
                    remainingOrphanCommits += c.getCommitShas().split(",").length;
                }
            }
        }
        participation.setOrphanCommitCount(remainingOrphanCommits);
        teamParticipationRepository.save(participation);
    }

}

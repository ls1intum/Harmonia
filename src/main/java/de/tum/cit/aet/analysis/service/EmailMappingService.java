package de.tum.cit.aet.analysis.service;

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
import de.tum.cit.aet.analysis.web.EmailMappingResource.TemplateAuthorDTO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Business logic for email-to-student mappings and template author management.
 * When a mapping is created or removed the CQI is recalculated
 * from the already-persisted LLM scores (no new LLM call).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailMappingService {

    private final ExerciseEmailMappingRepository emailMappingRepository;
    private final ExerciseTemplateAuthorRepository templateAuthorRepository;
    private final AnalyzedChunkRepository analyzedChunkRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final StudentRepository studentRepository;
    private final CqiRecalculationService cqiRecalculationService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ================================================================
    //  Email mapping operations
    // ================================================================

    /**
     * Returns all email mappings for the given exercise.
     *
     * @param exerciseId the exercise ID
     * @return list of email mapping DTOs
     */
    public List<EmailMappingDTO> getAllMappings(Long exerciseId) {
        return emailMappingRepository.findAllByExerciseId(exerciseId)
                .stream()
                .map(m -> new EmailMappingDTO(m.getId(), m.getExerciseId(),
                        m.getGitEmail(), m.getStudentId(), m.getStudentName(),
                        m.getIsDismissed()))
                .toList();
    }

    /**
     * Creates a new email-to-student mapping. Resolves the student ID by name,
     * re-assigns matching orphan chunks to the student, updates commit/line stats,
     * and recalculates the CQI.
     *
     * @param exerciseId the exercise ID
     * @param request    the mapping request with git email, student info and participation ID
     * @return updated client response DTO
     * @throws IllegalArgumentException      if participation not found or student ID is invalid
     * @throws EmailMappingConflictException  if a mapping for the email already exists
     */
    @Transactional
    public ClientResponseDTO createMapping(Long exerciseId, CreateEmailMappingRequestDTO request) {
        // 1. Find the team participation
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
            throw new IllegalArgumentException("Invalid student ID resolved for email mapping");
        }

        // 3. Check for existing mapping with the same email
        String normalizedEmail = request.gitEmail().toLowerCase(Locale.ROOT);
        if (emailMappingRepository.existsByExerciseIdAndGitEmail(exerciseId, normalizedEmail)) {
            throw new EmailMappingConflictException("Mapping already exists for email: " + normalizedEmail);
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
        recomputeAndPersistOrphanCount(participation, exerciseId);

        return buildResponse(participation);
    }

    /**
     * Dismisses an orphan email without assigning it to a student.
     * Chunks are NOT mutated; the dismissed mapping is used by clients
     * to filter them into a separate section.
     *
     * @param exerciseId the exercise ID
     * @param request    the dismiss request with git email and participation ID
     * @return updated client response, or empty if participation not found
     * @throws EmailMappingConflictException if a mapping for the email already exists
     */
    @Transactional
    public Optional<ClientResponseDTO> dismissEmail(Long exerciseId, DismissEmailRequestDTO request) {
        // 1. Normalize and check for duplicates
        String normalizedEmail = request.gitEmail().toLowerCase(Locale.ROOT);
        if (emailMappingRepository.existsByExerciseIdAndGitEmail(exerciseId, normalizedEmail)) {
            throw new EmailMappingConflictException("Mapping already exists for email: " + normalizedEmail);
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
            return Optional.of(buildResponse(participation));
        }
        return Optional.empty();
    }

    /**
     * Deletes an email mapping and reverts affected chunks back to external/orphan status.
     * Subtracts the chunk stats from the previously assigned student and recalculates CQI.
     *
     * @param exerciseId the exercise ID
     * @param mappingId  the mapping ID to delete
     * @return updated client response for the last affected team, or empty if no chunks changed
     * @throws IllegalArgumentException if mapping not found or does not belong to the exercise
     */
    @Transactional
    public Optional<ClientResponseDTO> deleteMapping(Long exerciseId, UUID mappingId) {
        ExerciseEmailMapping mapping = emailMappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + mappingId));

        if (!mapping.getExerciseId().equals(exerciseId)) {
            throw new IllegalArgumentException("Mapping does not belong to exercise " + exerciseId);
        }

        emailMappingRepository.delete(mapping);

        // Find all participations for this exercise and update chunks
        List<TeamParticipation> participations = teamParticipationRepository
                .findAllByExerciseId(exerciseId);

        String emailLower = mapping.getGitEmail().toLowerCase(Locale.ROOT);
        ClientResponseDTO lastResponse = null;

        for (TeamParticipation participation : participations) {
            List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
            List<AnalyzedChunk> orphanedChunks = new ArrayList<>();

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

        return Optional.ofNullable(lastResponse);
    }

    // ================================================================
    //  Template author operations
    // ================================================================

    /**
     * Returns all configured template authors for the given exercise.
     *
     * @param exerciseId the exercise ID
     * @return list of template author DTOs
     */
    public List<TemplateAuthorDTO> getTemplateAuthors(Long exerciseId) {
        return templateAuthorRepository.findByExerciseId(exerciseId)
                .stream()
                .map(ta -> new TemplateAuthorDTO(ta.getTemplateEmail(), ta.getAutoDetected()))
                .toList();
    }

    /**
     * Replaces all template authors for an exercise. Chunks from removed template emails
     * become regular orphans if not known via students or mappings. Chunks matching new
     * template emails are marked as external. CQI is recalculated for all teams.
     *
     * @param exerciseId the exercise ID
     * @param request    list of template author DTOs with emails
     * @return list of updated client response DTOs for all teams
     */
    @Transactional
    public List<ClientResponseDTO> setTemplateAuthors(Long exerciseId, List<TemplateAuthorDTO> request) {
        // Collect old emails
        Set<String> oldEmails = templateAuthorRepository.findByExerciseId(exerciseId)
                .stream()
                .map(ta -> ta.getTemplateEmail().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // Delete all existing, flush, then save new (deduplicated, lowercase)
        templateAuthorRepository.deleteByExerciseId(exerciseId);
        templateAuthorRepository.flush();
        Set<String> newEmails = new LinkedHashSet<>();
        for (TemplateAuthorDTO dto : request) {
            String email = dto.templateEmail().toLowerCase(Locale.ROOT);
            if (newEmails.add(email)) {
                templateAuthorRepository.save(
                        new ExerciseTemplateAuthor(exerciseId, email, false));
            }
        }

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
                    boolean shouldBeExternal = !knownEmails.contains(chunkEmail);
                    chunk.setIsExternalContributor(shouldBeExternal);
                    changed = true;
                }
                if (chunkEmail != null && newEmails.contains(chunkEmail)) {
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

        return responses;
    }

    /**
     * Removes all template author configurations for an exercise.
     * Chunks from old template authors that match a known student or mapping
     * are unmarked as external. CQI is recalculated for all teams.
     *
     * @param exerciseId the exercise ID
     * @return list of updated client response DTOs, or empty if none were configured
     */
    @Transactional
    public Optional<List<ClientResponseDTO>> deleteTemplateAuthors(Long exerciseId) {
        List<ExerciseTemplateAuthor> existing = templateAuthorRepository.findByExerciseId(exerciseId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Set<String> oldEmails = existing.stream()
                .map(ta -> ta.getTemplateEmail().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        templateAuthorRepository.deleteByExerciseId(exerciseId);
        templateAuthorRepository.flush();

        List<TeamParticipation> participations = teamParticipationRepository
                .findAllByExerciseId(exerciseId);

        List<ClientResponseDTO> responses = new ArrayList<>();
        for (TeamParticipation participation : participations) {
            List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);

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

        return Optional.of(responses);
    }

    // ================================================================
    //  Internal helpers
    // ================================================================

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

    ClientResponseDTO buildResponse(TeamParticipation participation) {
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
                null,
                loadAnalyzedChunkDTOs(participation),
                null,
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

    private void recomputeAndPersistOrphanCount(TeamParticipation participation, Long exerciseId) {
        List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
        Set<String> templateAuthorEmails = templateAuthorRepository.findByExerciseId(exerciseId)
                .stream().map(ta -> ta.getTemplateEmail().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        int remainingOrphanCommits = 0;
        for (AnalyzedChunk c : chunks) {
            if (Boolean.TRUE.equals(c.getIsExternalContributor())) {
                String chunkEmail = c.getAuthorEmail() != null ? c.getAuthorEmail().toLowerCase(Locale.ROOT) : null;
                if (chunkEmail != null && templateAuthorEmails.contains(chunkEmail)) {
                    continue;
                }
                if (c.getCommitShas() != null && !c.getCommitShas().isEmpty()) {
                    remainingOrphanCommits += c.getCommitShas().split(",").length;
                }
            }
        }
        participation.setOrphanCommitCount(remainingOrphanCommits);
        teamParticipationRepository.save(participation);
    }

    /**
     * Exception indicating a conflicting email mapping already exists.
     */
    public static class EmailMappingConflictException extends RuntimeException {
        public EmailMappingConflictException(String message) {
            super(message);
        }
    }
}

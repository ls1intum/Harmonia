package de.tum.cit.aet.dataProcessing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.ai.dto.AnalyzedChunkDTO;
import de.tum.cit.aet.ai.dto.LlmTokenTotalsDTO;
import de.tum.cit.aet.ai.dto.LlmTokenUsageDTO;
import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.dto.cqi.CQIResultDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentScoresDTO;
import de.tum.cit.aet.analysis.dto.cqi.ComponentWeightsDTO;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.analysis.service.AnalysisStateService;
import de.tum.cit.aet.analysis.service.cqi.CQICalculatorService;
import de.tum.cit.aet.dataProcessing.domain.AnalysisMode;
import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.domain.Tutor;
import de.tum.cit.aet.repositoryProcessing.dto.ClientResponseDTO;
import de.tum.cit.aet.repositoryProcessing.dto.StudentAnalysisDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamSummaryDTO;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Read-only queries and DTO mapping for analysis results.
 */
@Service
@Slf4j
public class AnalysisQueryService {

    private final TeamParticipationRepository teamParticipationRepository;
    private final StudentRepository studentRepository;
    private final AnalyzedChunkRepository analyzedChunkRepository;
    private final AnalysisStateService analysisStateService;
    private final CQICalculatorService cqiCalculatorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysisQueryService(TeamParticipationRepository teamParticipationRepository,
                                StudentRepository studentRepository,
                                AnalyzedChunkRepository analyzedChunkRepository,
                                AnalysisStateService analysisStateService,
                                CQICalculatorService cqiCalculatorService) {
        this.teamParticipationRepository = teamParticipationRepository;
        this.studentRepository = studentRepository;
        this.analyzedChunkRepository = analyzedChunkRepository;
        this.analysisStateService = analysisStateService;
        this.cqiCalculatorService = cqiCalculatorService;
    }

    /**
     * Returns all team analysis results for the given exercise.
     *
     * @param exerciseId the exercise id
     * @return list of client response DTOs, empty if none found
     */
    public List<ClientResponseDTO> getTeamsByExerciseId(Long exerciseId) {
        List<TeamParticipation> participations = teamParticipationRepository.findAllByExerciseId(exerciseId);
        if (participations.isEmpty()) {
            return List.of();
        }
        return participations.stream()
                .map(this::mapParticipationToClientResponse)
                .toList();
    }

    /**
     * Returns analysis results for all team participations across all exercises.
     *
     * @return list of client response DTOs
     */
    public List<ClientResponseDTO> getAllRepositoryData() {
        return teamParticipationRepository.findAll().stream()
                .map(this::mapParticipationToClientResponse)
                .toList();
    }

    /**
     * Checks whether any team in the exercise has a non-null CQI score persisted.
     *
     * @param exerciseId the exercise id
     * @return {@code true} if at least one team has analyzed data
     */
    public boolean hasAnalyzedDataForExercise(Long exerciseId) {
        return teamParticipationRepository.existsByExerciseIdAndCqiIsNotNull(exerciseId);
    }

    /**
     * Returns lightweight team summaries for the given exercise.
     *
     * @param exerciseId the exercise id
     * @return list of summary DTOs
     */
    public List<TeamSummaryDTO> getTeamSummariesByExerciseId(Long exerciseId) {
        return getTeamsByExerciseId(exerciseId).stream()
                .map(TeamSummaryDTO::fromClientResponse)
                .toList();
    }

    /**
     * Returns the full analysis detail for a single team.
     *
     * @param exerciseId the exercise id
     * @param teamId     the Artemis team id
     * @return the client response DTO, or empty if the team is not found
     */
    public Optional<ClientResponseDTO> getTeamDetail(Long exerciseId, Long teamId) {
        return teamParticipationRepository.findByExerciseIdAndTeam(exerciseId, teamId)
                .map(this::mapParticipationToClientResponse);
    }

    /**
     * Maps a persisted {@link TeamParticipation} entity to a client-facing response DTO,
     * including students, CQI details, analyzed chunks, and token usage.
     *
     * @param participation the team participation entity
     * @return fully populated client response DTO
     */
    public ClientResponseDTO mapParticipationToClientResponse(TeamParticipation participation) {
        List<Student> students = studentRepository.findAllByTeam(participation);
        Tutor tutor = participation.getTutor();

        List<StudentAnalysisDTO> studentDtos = students.stream()
                .map(s -> new StudentAnalysisDTO(s.getName(), s.getCommitCount(), s.getLinesAdded(),
                        s.getLinesDeleted(), s.getLinesChanged()))
                .toList();

        Double cqi = participation.getCqi();
        Boolean isSuspicious = participation.getIsSuspicious() != null ? participation.getIsSuspicious() : false;

        AnalysisMode mode = analysisStateService.getStatus(participation.getExerciseId()).getAnalysisMode();
        CQIResultDTO cqiDetails = reconstructCqiDetails(participation, mode);

        return new ClientResponseDTO(
                tutor != null ? tutor.getName() : "Unassigned",
                participation.getTeam(), participation.getName(),
                participation.getSubmissionCount(),
                studentDtos, cqi, isSuspicious,
                participation.getAnalysisStatus(),
                cqiDetails,
                loadAnalyzedChunks(participation),
                null,
                readTeamTokenTotals(participation),
                participation.getOrphanCommitCount(),
                participation.getIsFailed());
    }

    /**
     * Loads all analyzed chunks for a team participation and converts them to DTOs.
     *
     * @param participation the team participation that owns the chunks
     * @return list of analyzed chunk DTOs, or {@code null} if none exist or on error
     */
    public List<AnalyzedChunkDTO> loadAnalyzedChunks(TeamParticipation participation) {
        try {
            List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(participation);
            if (chunks.isEmpty()) {
                return null;
            }
            return chunks.stream()
                    .map(chunk -> new AnalyzedChunkDTO(
                            chunk.getChunkIdentifier(), chunk.getAuthorEmail(), chunk.getAuthorName(),
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
                                            + (chunk.getLlmCompletionTokens() != null ? chunk.getLlmCompletionTokens() : 0L),
                                    Boolean.TRUE.equals(chunk.getLlmUsageAvailable()))))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load analyzed chunks for team {}: {}", participation.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Reads the persisted LLM token usage totals from a team participation.
     *
     * @param tp the team participation entity
     * @return token totals DTO, or {@code null} if no usage data is stored
     */
    public LlmTokenTotalsDTO readTeamTokenTotals(TeamParticipation tp) {
        if (tp.getLlmCalls() == null && tp.getLlmCallsWithUsage() == null
                && tp.getLlmPromptTokens() == null && tp.getLlmCompletionTokens() == null
                && tp.getLlmTotalTokens() == null) {
            return null;
        }
        long prompt = tp.getLlmPromptTokens() != null ? tp.getLlmPromptTokens() : 0L;
        long completion = tp.getLlmCompletionTokens() != null ? tp.getLlmCompletionTokens() : 0L;
        long total = tp.getLlmTotalTokens() != null ? tp.getLlmTotalTokens() : prompt + completion;
        return new LlmTokenTotalsDTO(
                tp.getLlmCalls() != null ? tp.getLlmCalls() : 0L,
                tp.getLlmCallsWithUsage() != null ? tp.getLlmCallsWithUsage() : 0L,
                prompt, completion, total);
    }

    /**
     * Reconstructs a {@link CQIResultDTO} from the persisted component scores and weights.
     *
     * @param participation the team participation with stored CQI components
     * @param mode          the analysis mode used to select weight configuration
     * @return reconstructed CQI result, or {@code null} if no component scores are stored
     */
    public CQIResultDTO reconstructCqiDetails(TeamParticipation participation, AnalysisMode mode) {
        if (participation.getCqiEffortBalance() == null && participation.getCqiLocBalance() == null
                && participation.getCqiTemporalSpread() == null && participation.getCqiOwnershipSpread() == null) {
            return null;
        }

        ComponentScoresDTO components = new ComponentScoresDTO(
                participation.getCqiEffortBalance() != null ? participation.getCqiEffortBalance() : 0.0,
                participation.getCqiLocBalance() != null ? participation.getCqiLocBalance() : 0.0,
                participation.getCqiTemporalSpread() != null ? participation.getCqiTemporalSpread() : 0.0,
                participation.getCqiOwnershipSpread() != null ? participation.getCqiOwnershipSpread() : 0.0,
                participation.getCqiPairProgramming(),
                participation.getCqiPairProgrammingStatus());

        ComponentWeightsDTO weights;
        if (mode == AnalysisMode.FULL) {
            weights = cqiCalculatorService.buildWeightsDTO();
        } else if (mode == AnalysisMode.SIMPLE) {
            weights = cqiCalculatorService.buildRenormalizedWeightsWithoutEffort();
        } else {
            boolean hasEffortBalance = participation.getCqiEffortBalance() != null
                    && participation.getCqiEffortBalance() > 0;
            weights = hasEffortBalance
                    ? cqiCalculatorService.buildWeightsDTO()
                    : cqiCalculatorService.buildRenormalizedWeightsWithoutEffort();
        }

        return new CQIResultDTO(
                participation.getCqi() != null ? participation.getCqi() : 0.0,
                components,
                weights,
                participation.getCqiBaseScore() != null ? participation.getCqiBaseScore() : 0.0,
                null);
    }

    /**
     * Parses a JSON string into a list of commit messages.
     *
     * @param json the JSON array string
     * @return list of commit messages, or an empty list on error
     */
    @SuppressWarnings("unchecked")
    public List<String> parseCommitMessages(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return List.of();
            }
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Deserializes a JSON string into a list of weekly effort distribution values.
     *
     * @param json the JSON array string
     * @return list of weekly effort values, or {@code null} if empty or on error
     */
    public List<Double> deserializeWeeklyDistribution(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize weekly distribution: {}", e.getMessage());
            return null;
        }
    }
}

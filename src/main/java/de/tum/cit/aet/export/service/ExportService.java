package de.tum.cit.aet.export.service;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.analysis.repository.AnalyzedChunkRepository;
import de.tum.cit.aet.export.dto.*;
import de.tum.cit.aet.repositoryProcessing.domain.Student;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import de.tum.cit.aet.repositoryProcessing.domain.VCSLog;
import de.tum.cit.aet.repositoryProcessing.repository.StudentRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamParticipationRepository;
import de.tum.cit.aet.repositoryProcessing.repository.TeamRepositoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class ExportService {

    private final TeamParticipationRepository teamParticipationRepository;
    private final StudentRepository studentRepository;
    private final AnalyzedChunkRepository analyzedChunkRepository;
    private final TeamRepositoryRepository teamRepositoryRepository;

    public ExportService(TeamParticipationRepository teamParticipationRepository,
            StudentRepository studentRepository,
            AnalyzedChunkRepository analyzedChunkRepository,
            TeamRepositoryRepository teamRepositoryRepository) {
        this.teamParticipationRepository = teamParticipationRepository;
        this.studentRepository = studentRepository;
        this.analyzedChunkRepository = analyzedChunkRepository;
        this.teamRepositoryRepository = teamRepositoryRepository;
    }

    /**
     * Export analyzed data for an exercise in the requested format.
     *
     * @param exerciseId the exercise ID
     * @param format     the export format (EXCEL, JSON)
     * @param include    set of data scopes to include (teams, students, chunks, commits)
     * @return the exported file content as byte array
     * @throws IOException if serialization fails
     */
    @Transactional(readOnly = true)
    public byte[] exportData(Long exerciseId, ExportFormat format, Set<String> include) throws IOException {
        List<TeamParticipation> participations = teamParticipationRepository.findAllByExerciseId(exerciseId);
        log.info("Exporting data for exercise {} with {} teams, format={}, include={}",
                exerciseId, participations.size(), format, include);

        List<TeamExportRow> teamRows = new ArrayList<>();
        List<StudentExportRow> studentRows = new ArrayList<>();
        List<ChunkExportRow> chunkRows = new ArrayList<>();
        List<CommitExportRow> commitRows = new ArrayList<>();

        for (TeamParticipation tp : participations) {
            String teamName = tp.getName();
            String tutorName = tp.getTutor() != null ? tp.getTutor().getName() : null;

            if (include.contains("teams")) {
                teamRows.add(new TeamExportRow(
                        teamName,
                        tp.getShortName(),
                        tutorName,
                        sanitizeUrl(tp.getRepositoryUrl()),
                        tp.getSubmissionCount(),
                        tp.getAnalysisStatus() != null ? tp.getAnalysisStatus().name() : null,
                        tp.getCqi(),
                        tp.getCqiBaseScore(),
                        tp.getCqiPenaltyMultiplier(),
                        tp.getCqiEffortBalance(),
                        tp.getCqiLocBalance(),
                        tp.getCqiTemporalSpread(),
                        tp.getCqiOwnershipSpread(),
                        tp.getCqiPairProgramming(),
                        tp.getCqiPairProgrammingStatus(),
                        tp.getIsSuspicious(),
                        tp.getLlmTotalTokens()));
            }

            if (include.contains("students")) {
                List<Student> students = studentRepository.findAllByTeam(tp);
                for (Student s : students) {
                    studentRows.add(new StudentExportRow(
                            teamName,
                            s.getName(),
                            s.getLogin(),
                            s.getEmail(),
                            s.getCommitCount(),
                            s.getLinesAdded(),
                            s.getLinesDeleted(),
                            s.getLinesChanged()));
                }
            }

            if (include.contains("chunks")) {
                List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(tp);
                for (AnalyzedChunk c : chunks) {
                    chunkRows.add(new ChunkExportRow(
                            teamName,
                            c.getAuthorName(),
                            c.getAuthorEmail(),
                            c.getClassification(),
                            c.getEffortScore(),
                            c.getComplexity(),
                            c.getNovelty(),
                            c.getConfidence(),
                            c.getReasoning(),
                            c.getCommitShas(),
                            c.getCommitMessages(),
                            c.getTimestamp(),
                            c.getLinesChanged(),
                            c.getIsBundled(),
                            c.getChunkIndex(),
                            c.getTotalChunks(),
                            c.getIsError(),
                            c.getErrorMessage(),
                            c.getLlmModel(),
                            c.getLlmPromptTokens(),
                            c.getLlmCompletionTokens(),
                            c.getLlmTotalTokens(),
                            c.getLlmUsageAvailable()));
                }
            }

            if (include.contains("commits")) {
                teamRepositoryRepository.findByTeamParticipation(tp).ifPresent(repo -> {
                    if (repo.getVcsLogs() != null) {
                        for (VCSLog vcsLog : repo.getVcsLogs()) {
                            commitRows.add(new CommitExportRow(
                                    teamName,
                                    vcsLog.getCommitHash(),
                                    vcsLog.getEmail()));
                        }
                    }
                });
            }
        }

        ExportData exportData = new ExportData(
                include.contains("teams") ? teamRows : null,
                include.contains("students") ? studentRows : null,
                include.contains("chunks") ? chunkRows : null,
                include.contains("commits") ? commitRows : null);

        return switch (format) {
            case EXCEL -> ExcelExporter.export(exportData);
            case JSON -> JsonExporter.export(exportData);
        };
    }

    private static String sanitizeUrl(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getUserInfo() != null) {
                return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                        uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
            }
            return url;
        } catch (Exception e) {
            return url;
        }
    }
}

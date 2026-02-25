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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service responsible for collecting and serializing exercise data for export.
 * Gathers team, student, chunk, and commit data from their respective repositories
 * and delegates the final serialization to format-specific exporters.
 */
@Service
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
     * Collects the requested data scopes for a given exercise and serializes them
     * into the specified format.
     *
     * @param exerciseId the ID of the exercise to export
     * @param format     the desired output format ({@link ExportFormat#EXCEL} or {@link ExportFormat#JSON})
     * @param include    set of data scopes to include (e.g. "teams", "students", "chunks", "commits")
     * @return the serialized file content as a byte array
     * @throws IOException if serialization fails
     */
    @Transactional(readOnly = true)
    public byte[] exportData(Long exerciseId, ExportFormat format, Set<String> include) throws IOException {
        // 1) Load all team participations for the exercise
        List<TeamParticipation> participations = teamParticipationRepository.findAllByExerciseId(exerciseId);

        // 2) Collect rows for each requested data scope
        List<TeamExportRow> teamRows = new ArrayList<>();
        List<StudentExportRow> studentRows = new ArrayList<>();
        List<ChunkExportRow> chunkRows = new ArrayList<>();
        List<CommitExportRow> commitRows = new ArrayList<>();

        for (TeamParticipation tp : participations) {
            String teamName = tp.getName();
            String tutorName = tp.getTutor() != null ? tp.getTutor().getName() : null;

            if (include.contains("teams")) {
                teamRows.add(buildTeamRow(tp, teamName, tutorName));
            }
            if (include.contains("students")) {
                collectStudentRows(tp, teamName, studentRows);
            }
            if (include.contains("chunks")) {
                collectChunkRows(tp, teamName, chunkRows);
            }
            if (include.contains("commits")) {
                collectCommitRows(tp, teamName, commitRows);
            }
        }

        // 3) Assemble the export data and serialize
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

    private TeamExportRow buildTeamRow(TeamParticipation tp, String teamName, String tutorName) {
        return new TeamExportRow(
                teamName,
                tp.getShortName(),
                tutorName,
                sanitizeUrl(tp.getRepositoryUrl()),
                tp.getSubmissionCount(),
                tp.getAnalysisStatus() != null ? tp.getAnalysisStatus().name() : null,
                tp.getCqi(),
                tp.getCqiEffortBalance(),
                tp.getCqiLocBalance(),
                tp.getCqiTemporalSpread(),
                tp.getCqiOwnershipSpread(),
                tp.getCqiPairProgramming(),
                tp.getCqiPairProgrammingStatus(),
                tp.getIsSuspicious(),
                tp.getLlmTotalTokens());
    }

    private void collectStudentRows(TeamParticipation tp, String teamName, List<StudentExportRow> rows) {
        List<Student> students = studentRepository.findAllByTeam(tp);
        for (Student s : students) {
            rows.add(new StudentExportRow(
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

    private void collectChunkRows(TeamParticipation tp, String teamName, List<ChunkExportRow> rows) {
        List<AnalyzedChunk> chunks = analyzedChunkRepository.findByParticipation(tp);
        for (AnalyzedChunk c : chunks) {
            rows.add(new ChunkExportRow(
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

    private void collectCommitRows(TeamParticipation tp, String teamName, List<CommitExportRow> rows) {
        teamRepositoryRepository.findByTeamParticipation(tp).ifPresent(repo -> {
            if (repo.getVcsLogs() != null) {
                for (VCSLog vcsLog : repo.getVcsLogs()) {
                    rows.add(new CommitExportRow(
                            teamName,
                            vcsLog.getCommitHash(),
                            vcsLog.getEmail()));
                }
            }
        });
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

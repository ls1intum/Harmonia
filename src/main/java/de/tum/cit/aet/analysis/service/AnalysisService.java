package de.tum.cit.aet.analysis.service;

import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AnalysisService {

    private final GitContributionAnalysisService gitContributionAnalysisService;

    @Autowired
    public AnalysisService(GitContributionAnalysisService gitContributionAnalysisService) {
        this.gitContributionAnalysisService = gitContributionAnalysisService;
    }

    /**
     * Analyzes contributions across multiple repositories.
     *
     * @param repositories List of TeamRepositoryDTO representing the repositories to analyze.
     * @return A map where the key is the author ID and the value is an array with total lines added and removed and the number of commits.
     */
    public Map<Long, AuthorContributionDTO> analyzeContributions(List<TeamRepositoryDTO> repositories) {
        log.info("Starting contribution analysis");
        Map<Long, AuthorContributionDTO> result = gitContributionAnalysisService.processAllRepositories(repositories);
        log.info("Contribution analysis completed");
        return result;
    }

    public Map<Long, AuthorContributionDTO> analyzeRepository(TeamRepositoryDTO repo) {
        return gitContributionAnalysisService.analyzeRepository(repo);
    }
}

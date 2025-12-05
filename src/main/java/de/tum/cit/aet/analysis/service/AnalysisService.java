package de.tum.cit.aet.analysis.service;

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

    public Map<Long, int[]> analyzeContributions(List<TeamRepositoryDTO> repositories) {
        log.info("Starting contribution analysis");
        Map<Long, int[]> result = gitContributionAnalysisService.processAllRepositories(repositories);
        log.info("Contribution analysis completed");
        return result;
    }
}

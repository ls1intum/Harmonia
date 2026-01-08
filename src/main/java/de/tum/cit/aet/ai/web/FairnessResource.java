package de.tum.cit.aet.ai.web;

import de.tum.cit.aet.ai.dto.FairnessReportDTO;
import de.tum.cit.aet.ai.service.ContributionFairnessService;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for exposing contribution fairness analysis.
 */
@RestController
@RequestMapping("api/ai/fairness")
@Slf4j
@RequiredArgsConstructor
@Profile("!openapi")
public class FairnessResource {

    private final ContributionFairnessService fairnessService;

    /**
     * Analyzes contribution fairness for a given repository.
     * 
     * @param repository The repository DTO containing local path and commit logs
     * @return The fairness report
     */
    @PostMapping("/analyze")
    public FairnessReportDTO analyzeFairness(@RequestBody TeamRepositoryDTO repository) {
        log.info("Received request to analyze fairness for repo: {}",
                repository.localPath() != null ? repository.localPath() : "unknown");
        return fairnessService.analyzeFairness(repository);
    }
}

package de.tum.cit.aet.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.analysis.domain.TeamAnalysis;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamAnalysisDTO(UUID teamAnalysisId) {

    /**
     * @return The teamAnalysisDTO from the teamAnalysis
     */
    public static TeamAnalysisDTO getFromEntity(TeamAnalysis teamAnalysis) {
        if (teamAnalysis == null) {
            return null;
        }

        return new TeamAnalysisDTO(
                teamAnalysis.getTeamAnalysisId()
        );
    }
}

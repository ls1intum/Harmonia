package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * DTO representing the response sent to clients.
 *
 * @param tutor           The name of the tutor associated with the team.
 * @param teamId          The unique identifier of the team.
 * @param teamName        The full name of the team.
 * @param submissionCount The number of submissions made by the team.
 * @param students        A list of StudentAnalysisDTO representing individual student analyses within the team.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientResponseDTO(
        String tutor,
        Long teamId,
        String teamName,
        Integer submissionCount,
        List<StudentAnalysisDTO> students
) {
}

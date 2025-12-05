package de.tum.cit.aet.repositoryProcessing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.repositoryProcessing.domain.Student;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientResponseDTO(
        String tutor,
        Long teamId,
        String teamName,
        String shortName,
        Integer submissionCount,
        List<StudentAnalysisDTO> students
) {
    public ClientResponseDTO(String tutor, Long teamId, String teamName, String shortName, Integer submissionCount, List<Student> students) {
        this(tutor, teamId, teamName, shortName, submissionCount,
                students.stream().map(student -> (new StudentAnalysisDTO(
                                student.getName(),
                                student.getCommitCount(),
                                student.getLinesAdded(),
                                student.getLinesDeleted(),
                                student.getLinesChanged())))
                        .toList());
    }
}


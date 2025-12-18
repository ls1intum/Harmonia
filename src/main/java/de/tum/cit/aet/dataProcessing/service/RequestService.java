package de.tum.cit.aet.dataProcessing.service;

import de.tum.cit.aet.analysis.dto.AuthorContributionDTO;
import de.tum.cit.aet.analysis.service.AnalysisService;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.repositoryProcessing.domain.*;
import de.tum.cit.aet.repositoryProcessing.dto.*;
import de.tum.cit.aet.repositoryProcessing.repository.*;
import de.tum.cit.aet.repositoryProcessing.service.RepositoryFetchingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RequestService {

    private final RepositoryFetchingService repositoryFetchingService;
    private final AnalysisService analysisService;

    private final TeamRepositoryRepository teamRepositoryRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final TutorRepository tutorRepository;
    private final StudentRepository studentRepository;

    @Autowired
    public RequestService(RepositoryFetchingService repositoryFetchingService, AnalysisService analysisService, TeamRepositoryRepository teamRepositoryRepository, TeamParticipationRepository teamParticipationRepository, TutorRepository tutorRepository, StudentRepository studentRepository) {
        this.repositoryFetchingService = repositoryFetchingService;
        this.analysisService = analysisService;
        this.teamRepositoryRepository = teamRepositoryRepository;
        this.teamParticipationRepository = teamParticipationRepository;
        this.tutorRepository = tutorRepository;
        this.studentRepository = studentRepository;
    }

    /**
     * Fetches, analyzes, and saves repository data using the provided Artemis credentials.
     *
     * @param credentials The Artemis credentials
     * @param exerciseId  The exercise ID to fetch participations for
     */
    public void fetchAnalyzeAndSaveRepositories(ArtemisCredentials credentials, Long exerciseId) {
        // Fetch and clone repositories
        List<TeamRepositoryDTO> repositories = fetchAndCloneRepositories(credentials, exerciseId);

        // Analyze contributions
        Map<Long, AuthorContributionDTO> contributionData = getContributionData(repositories);

        // Save results to the database
        saveResults(repositories, contributionData);
    }

    /**
     * Fetches and clones all repositories from Artemis using dynamic credentials.
     *
     * @param credentials The Artemis credentials
     * @param exerciseId  The exercise ID to fetch participations for
     * @return List of TeamRepositoryDTO containing repository information
     */
    public List<TeamRepositoryDTO> fetchAndCloneRepositories(ArtemisCredentials credentials, Long exerciseId) {
        log.info("RequestService: Initiating repository fetch and clone process");
        return repositoryFetchingService.fetchAndCloneRepositories(credentials, exerciseId);
    }

    /**
     * Analyzes contributions for the given list of repositories.
     *
     * @param repositories List of TeamRepositoryDTO to be analyzed
     * @return Map of Participant ID to an array of contribution metrics (e.g., lines added, lines deleted)
     */
    public Map<Long, AuthorContributionDTO> getContributionData(List<TeamRepositoryDTO> repositories) {
        return analysisService.analyzeContributions(repositories);
    }

    /**
     * Saves the fetched repository information into the database.
     *
     * @param repositories     List of TeamRepositoryDTO to be saved
     * @param contributionData Map of Participant ID to an array of contribution metrics (e.g., lines added, lines deleted)
     */
    public void saveResults(List<TeamRepositoryDTO> repositories, Map<Long, AuthorContributionDTO> contributionData) {
        // TODO: Implement a better strategy for updating existing records instead of deleting all data
        // Clear existing data in database tables. We assume a full refresh of all data is intended, effectively treating the run as idempotent
        teamRepositoryRepository.deleteAll();
        studentRepository.deleteAll();
        teamParticipationRepository.deleteAll();
        tutorRepository.deleteAll();

        for (TeamRepositoryDTO repo : repositories) {
            // Save tutor
            ParticipantDTO tut = repo.participation().team().owner();
            Tutor tutor = null;
            if (tut == null) {
                log.warn("No tutor found for team: {}", repo.participation().team().name());
            } else {
                tutor = new Tutor(tut.id(), tut.login(), tut.name(), tut.email());
                tutorRepository.save(tutor);
            }

            // Save team participation
            ParticipationDTO participation = repo.participation();
            TeamDTO team = participation.team();
            TeamParticipation teamParticipation = new TeamParticipation(participation.id(), team.id(), tutor, team.name(), team.shortName(), participation.repositoryUri(), participation.submissionCount());
            teamParticipationRepository.save(teamParticipation);

            // Save students with contributions
            List<Student> students = new ArrayList<>();
            for (ParticipantDTO student : repo.participation().team().students()) {
                AuthorContributionDTO contributionDTO = contributionData.get(student.id());

                // Handle the case where a student made no contributions (e.g., if they were registered but never committed)
                if (contributionDTO == null) {
                    contributionDTO = new AuthorContributionDTO(0, 0, 0);
                }

                students.add(new Student(student.id(), student.login(), student.name(), student.email(), teamParticipation,
                        contributionDTO.commitCount(), contributionDTO.linesAdded(), contributionDTO.linesDeleted(),
                        contributionDTO.linesAdded() + contributionDTO.linesDeleted()));
            }
            studentRepository.saveAll(students);

            // Save team repository
            TeamRepository teamRepo = new TeamRepository(teamParticipation, null, repo.localPath(), repo.isCloned(), repo.error());

            // Process VCS logs
            List<VCSLog> vcsLogs = repo.vcsLogs().stream().map(log -> new VCSLog(teamRepo, log.commitHash(), log.email())).toList();

            // Save the TeamRepository (through cascade, VCSLogs will also be saved)
            teamRepo.setVcsLogs(vcsLogs);
            teamRepositoryRepository.save(teamRepo);

            log.info("Processed repository for team: {}", team.name());
        }
    }

    /**
     * Retrieves all stored repository data from the database and assembles it into ClientResponseDTOs.
     *
     * @return List of ClientResponseDTO containing the assembled data
     */
    public List<ClientResponseDTO> getAllRepositoryData() {
        log.info("RequestService: Initiating data extraction from database");

        // Fetch all TeamParticipation records
        List<TeamParticipation> participations = teamParticipationRepository.findAll();

        // Map and assemble the data into ClientResponseDTOs
        List<ClientResponseDTO> responseDTOs = participations.stream()
                .map(participation -> {
                    List<Student> students = studentRepository.findAllByTeam(participation);

                    Tutor tutor = participation.getTutor();

                    List<StudentAnalysisDTO> studentAnalysisDTOS = students.stream().map(student -> (new StudentAnalysisDTO(
                                    student.getName(),
                                    student.getCommitCount(),
                                    student.getLinesAdded(),
                                    student.getLinesDeleted(),
                                    student.getLinesChanged())))
                            .toList();

                    return new ClientResponseDTO(
                            tutor != null ? tutor.getName() : "Unassigned",
                            participation.getTeam(),
                            participation.getName(),
                            participation.getSubmissionCount(),
                            studentAnalysisDTOS
                    );
                })
                .toList();

        log.info("RequestService: Extracted {} team participation records from the database.", responseDTOs.size());
        return responseDTOs;
    }
}

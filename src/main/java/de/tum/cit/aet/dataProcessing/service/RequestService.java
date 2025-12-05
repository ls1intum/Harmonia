package de.tum.cit.aet.dataProcessing.service;

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
     * Fetches and clones all repositories from Artemis using dynamic credentials.
     *
     * @param credentials The Artemis credentials
     * @return List of TeamRepositoryDTO containing repository information
     */
    public List<TeamRepositoryDTO> fetchAndCloneRepositories(ArtemisCredentials credentials) {
        log.info("RequestService: Initiating repository fetch and clone process");
        return repositoryFetchingService.fetchAndCloneRepositories(credentials);
    }

    public Map<Long, int[]> getContributionData(List<TeamRepositoryDTO> repositories) {
        return analysisService.analyzeContributions(repositories);
    }

    public void fetchAnalyzeAndSaveRepositories(ArtemisCredentials credentials) {
        // Fetch and clone repositories
        List<TeamRepositoryDTO> repositories = fetchAndCloneRepositories(credentials);

        // Analyze contributions
        Map<Long, int[]> contributionData = getContributionData(repositories);
        for (int[] lines : contributionData.values()) {
            log.info("Contribution Data - Added Lines: {}, Deleted Lines: {}", lines[0], lines[1]);
        }

        // Save results to the database
        saveResults(repositories, contributionData);
    }

    /**
     * Saves the fetched repository information into the database.
     *
     * @param repositories List of TeamRepositoryDTO to be saved
     */
    public void saveResults(List<TeamRepositoryDTO> repositories, Map<Long, int[]> contributionData) {
        teamRepositoryRepository.deleteAll();
        studentRepository.deleteAll();
        teamParticipationRepository.deleteAll();
        tutorRepository.deleteAll();

        for (TeamRepositoryDTO repo : repositories) {
            ParticipantDTO tut = repo.participation().team().owner();
            Tutor tutor = null;
            if (tut == null) {
                log.warn("No tutor found for team: {}", repo.participation().team().name());
            } else {
                tutor = new Tutor(tut.id(), tut.login(), tut.name(), tut.email());
                tutorRepository.save(tutor);
            }

            ParticipationDTO participation = repo.participation();
            TeamDTO team = participation.team();
            TeamParticipation teamParticipation = new TeamParticipation(participation.id(), team.id(), tutor, team.name(), team.shortName(), participation.repositoryUri(), participation.submissionCount());
            teamParticipationRepository.save(teamParticipation);

            List<Student> students = new ArrayList<>();
            for (ParticipantDTO student : repo.participation().team().students()) {
                int[] lines = contributionData.get(student.id());
                if (lines == null) lines = new int[]{0, 0};
                students.add(new Student(student.id(), student.login(), student.name(), student.email(), teamParticipation, lines[0], lines[1], lines[0] + lines[1]));
            }
            studentRepository.saveAll(students);

            TeamRepository teamRepo = new TeamRepository(teamParticipation, null, repo.localPath(), repo.isCloned(), repo.error());

            List<VCSLog> vcsLogs = repo.vcsLogs().stream().map(log -> new VCSLog(teamRepo, log.commitHash(), log.email())).toList();

            teamRepo.setVcsLogs(vcsLogs);
            teamRepositoryRepository.save(teamRepo);

            log.info("Processed repository for team: {}", team.name());
        }
    }

    /**
     * Extracts all saved repository and contribution data from the database.
     *
     * @return A list of ClientResponseDTOs containing all aggregated team and student data.
     */
    public List<ClientResponseDTO> getAllRepositoryData() {
        log.info("RequestService: Initiating data extraction from database");

        // 1. Fetch all TeamParticipation records
        List<TeamParticipation> participations = teamParticipationRepository.findAll();

        // 2. Map and assemble the data into ClientResponseDTOs
        List<ClientResponseDTO> responseDTOs = participations.stream()
                .map(participation -> {
                    List<Student> students = studentRepository.findByTeamParticipation(participation);

                    Tutor tutor = participation.getTutor();

                    List<StudentAnalysisDTO> studentAnalysisDTOS = students.stream().map(student -> (new StudentAnalysisDTO(
                                    student.getName(),
                                    student.getCommitCount(),
                                    student.getLinesAdded(),
                                    student.getLinesDeleted(),
                                    student.getLinesChanged())))
                            .toList();

                    return new ClientResponseDTO(
                            tutor.getName(),
                            participation.getTeam(),
                            participation.getName(),
                            participation.getShortName(),
                            participation.getSubmissionCount(),
                            studentAnalysisDTOS
                    );
                })
                .toList();

        log.info("RequestService: Extracted {} team participation records.", responseDTOs.size());
        return responseDTOs;
    }
}

package de.tum.cit.aet.dataProcessing.service;

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

@Service
@Slf4j
public class RequestService {

    private final RepositoryFetchingService repositoryFetchingService;
    private final TeamRepositoryRepository teamRepositoryRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final TutorRepository tutorRepository;
    private final StudentRepository studentRepository;

    @Autowired
    public RequestService(RepositoryFetchingService repositoryFetchingService, TeamRepositoryRepository teamRepositoryRepository, TeamParticipationRepository teamParticipationRepository, TutorRepository tutorRepository, StudentRepository studentRepository) {
        this.repositoryFetchingService = repositoryFetchingService;
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
        List<TeamRepositoryDTO> repositories = repositoryFetchingService.fetchAndCloneRepositories(credentials);
        saveResults(repositories);
        return repositories;
    }



    /**
     * Saves the fetched repository information into the database.
     *
     * @param repositories List of TeamRepositoryDTO to be saved
     */
    public void saveResults(List<TeamRepositoryDTO> repositories) {
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
                students.add(new Student(student.id(), student.login(), student.name(), student.email(),teamParticipation));
            }
            studentRepository.saveAll(students);

            TeamRepository teamRepo = new TeamRepository(teamParticipation, null, repo.localPath(), repo.isCloned(), repo.error());

            List<VCSLog> vcsLogs = repo.vcsLogs().stream().map(log -> new VCSLog(teamRepo, log.commitHash(), log.email())).toList();

            teamRepo.setVcsLogs(vcsLogs);
            teamRepositoryRepository.save(teamRepo);

            log.info("Processed repository for team: {}", team.name());
        }
    }
}

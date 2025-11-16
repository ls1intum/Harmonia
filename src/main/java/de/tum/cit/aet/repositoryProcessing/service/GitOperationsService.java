package de.tum.cit.aet.repositoryProcessing.service;

import de.tum.cit.aet.core.config.ArtemisConfig;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for Git operations using JGit library.
 * Handles cloning, pulling, and analyzing repositories.
 */
@Service
@Slf4j
public class GitOperationsService {

    private final ArtemisConfig artemisConfig;

    @Autowired
    public GitOperationsService(ArtemisConfig artemisConfig) {
        this.artemisConfig = artemisConfig;
    }

    /**
     * Clones or pulls a repository. If the repository already exists locally, it performs a pull.
     * Otherwise, it clones the repository.
     *
     * @param repositoryUri The URI of the Git repository
     * @param teamName      The name of the team (used for directory naming)
     * @return The local path where the repository is stored
     * @throws GitAPIException If Git operations fail
     * @throws IOException     If file operations fail
     */
    public String cloneOrPullRepository(String repositoryUri, String teamName) throws GitAPIException, IOException {
        // Extracts the repository name from the URI
        String[] parts = repositoryUri.split("/");
        String repoName = parts[parts.length - 1].replace(".git", "");

        Path localPath = Paths.get(artemisConfig.getGitRepoPath(), repoName);
        File repoDir = localPath.toFile();

        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            log.info("Repository {} already exists, performing git pull", repoName);
            pullRepository(localPath);
        } else {
            log.info("Cloning repository {} for team {}", repoName, teamName);
            cloneRepository(repositoryUri, localPath);
        }

        return localPath.toString();
    }

    /**
     * Clones a repository from the given URI to the local path.
     *
     * @param repositoryUri The URI of the Git repository
     * @param localPath     The local path where the repository should be cloned
     * @throws GitAPIException If the clone operation fails
     */
    private void cloneRepository(String repositoryUri, Path localPath) throws GitAPIException {
        Git.cloneRepository()
                .setURI(repositoryUri)
                .setDirectory(localPath.toFile())
                .setCredentialsProvider(createCredentialsProvider())
                .call()
                .close();

        log.info("Successfully cloned repository to {}", localPath);
    }

    /**
     * Pulls the latest changes for an existing repository.
     *
     * @param localPath The local path of the repository
     * @throws GitAPIException If the pull operation fails
     * @throws IOException     If the repository cannot be opened
     */
    private void pullRepository(Path localPath) throws GitAPIException, IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder
                .setGitDir(new File(localPath.toFile(), ".git"))
                .readEnvironment()
                .findGitDir()
                .build();

        try (Git git = new Git(repository)) {
            git.pull()
                    .setCredentialsProvider(createCredentialsProvider())
                    .call();

            log.info("Successfully pulled latest changes for {}", localPath);
        }
    }

    /**
     * Counts the number of commits in a repository, excluding merge commits.
     *
     * @param localPath The local path of the repository
     * @return The number of commits
     * @throws IOException     If the repository cannot be opened
     * @throws GitAPIException If the log operation fails
     */
    public int countCommits(String localPath) throws IOException, GitAPIException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder
                .setGitDir(new File(localPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();

        int commitCount = 0;

        try (Git git = new Git(repository)) {
            Iterable<RevCommit> commits = git.log().call();

            for (RevCommit commit : commits) {
                // Exclude merge commits (commits with more than one parent)
                if (commit.getParentCount() <= 1) {
                    commitCount++;
                }
            }
        }

        log.debug("Repository {} has {} commits", localPath, commitCount);
        return commitCount;
    }

    /**
     * Creates credentials provider for Git authentication.
     *
     * @return UsernamePasswordCredentialsProvider with configured credentials
     */
    private UsernamePasswordCredentialsProvider createCredentialsProvider() {
        return new UsernamePasswordCredentialsProvider(
                artemisConfig.getUsername(),
                artemisConfig.getPassword()
        );
    }

    /**
     * Blabla
     *
     * @param participation to give
     * @return TeamRepositoryDTO
     */
    public TeamRepositoryDTO cloneTeamRepository(ParticipationDTO participation) {
        String teamName = participation.team() != null
                ? participation.team().name()
                : "Unknown Team";

        String repositoryUri = participation.repositoryUri();

        TeamRepositoryDTO.Builder builder = TeamRepositoryDTO.builder()
                .teamName(teamName)
                .repositoryUri(repositoryUri);

        try {
            String localPath = cloneOrPullRepository(repositoryUri, teamName);

            builder.localPath(localPath)
                    .isCloned(true);

            log.info("Successfully processed repository for team: {}", teamName);

        } catch (Exception e) {
            log.error("Failed to clone repository for team: {}", teamName, e);
            builder.isCloned(false)
                    .error(e.getMessage());
        }

        return builder.build();
    }
}
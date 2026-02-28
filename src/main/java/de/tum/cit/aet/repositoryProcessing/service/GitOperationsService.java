package de.tum.cit.aet.repositoryProcessing.service;

import de.tum.cit.aet.artemis.ArtemisClientService;
import de.tum.cit.aet.core.config.HarmoniaProperties;
import de.tum.cit.aet.core.dto.ArtemisCredentials;
import de.tum.cit.aet.core.exceptions.GitOperationException;
import de.tum.cit.aet.repositoryProcessing.dto.ParticipationDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTO;
import de.tum.cit.aet.repositoryProcessing.dto.TeamRepositoryDTOBuilder;
import de.tum.cit.aet.repositoryProcessing.dto.VCSLogDTO;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Service for Git repository operations.
 * Handles cloning/pulling repositories with exponential-backoff retry logic,
 * VCS log retrieval, and local path resolution.
 */
@Slf4j
@Service
public class GitOperationsService {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 2000;

    private final ArtemisClientService artemisClientService;
    private final HarmoniaProperties harmoniaProperties;

    public GitOperationsService(ArtemisClientService artemisClientService,
                                 HarmoniaProperties harmoniaProperties) {
        this.artemisClientService = artemisClientService;
        this.harmoniaProperties = harmoniaProperties;
    }

    /**
     * Clones (or pulls) a team repository and fetches its VCS access log from Artemis.
     * This is the single entry point for repository processing per team.
     *
     * @param participation the participation containing the repository URI
     * @param credentials   the Artemis credentials for Git and API access
     * @param exerciseId    the exercise ID (used to resolve the local repo path)
     * @return a {@link TeamRepositoryDTO} with clone status, local path, and VCS logs
     */
    public TeamRepositoryDTO cloneAndFetchLogs(ParticipationDTO participation,
                                                ArtemisCredentials credentials, Long exerciseId) {
        String teamName = participation.team() != null ? participation.team().name() : "Unknown Team";
        TeamRepositoryDTOBuilder builder = TeamRepositoryDTO.builder()
                .participation(participation);

        try {
            if (!credentials.hasGitCredentials()) {
                throw new IllegalStateException("No Git credentials provided. Username and password are required.");
            }

            // 1) Clone or pull the repository
            String gitRepoPath = resolveGitRepoPath(exerciseId);
            String localPath = cloneOrPullRepository(
                    participation.repositoryUri(),
                    credentials.username(), credentials.password(), gitRepoPath);

            // 2) Fetch VCS access logs from Artemis
            List<VCSLogDTO> vcsLogs = artemisClientService.fetchVCSAccessLog(
                    credentials.serverUrl(), credentials.jwtToken(), participation.id());

            builder.localPath(localPath)
                    .isCloned(true)
                    .vcsLogs(vcsLogs);
        } catch (Exception e) {
            log.error("Failed to process repository for team '{}'", teamName, e);
            builder.isCloned(false)
                    .error(e.getMessage())
                    .vcsLogs(List.of());
        }

        return builder.build();
    }

    // ---- Git operations ----

    private String cloneOrPullRepository(String repositoryUri,
                                          String username, String password, String gitRepoPath) {
        UsernamePasswordCredentialsProvider credentials =
                new UsernamePasswordCredentialsProvider(username, password);

        // 1) Derive local path from repository URI
        String[] parts = repositoryUri.split("/");
        String repoName = parts[parts.length - 1].replace(".git", "");
        Path localPath = Paths.get(gitRepoPath, repoName);
        File repoDir = localPath.toFile();

        // 2) Clone or pull depending on whether the repo already exists
        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            pullRepositoryWithRetry(localPath, credentials, repoName);
        } else {
            cloneRepositoryWithRetry(repositoryUri, localPath, credentials, repoName);
        }

        return localPath.toString();
    }

    private void cloneRepositoryWithRetry(String repositoryUri, Path localPath,
            UsernamePasswordCredentialsProvider credentials, String repoName) {
        int attempt = 0;
        long delayMs = INITIAL_RETRY_DELAY_MS;

        while (true) {
            try {
                cloneRepository(repositoryUri, localPath, credentials);
                return;
            } catch (GitOperationException e) {
                attempt++;
                if (isRetryableError(e) && attempt < MAX_RETRIES) {
                    log.warn("Clone attempt {} failed for {} (retrying in {}ms): {}",
                            attempt, repoName, delayMs, e.getMessage());
                    sleepOrThrow(delayMs);
                    delayMs *= 2;
                    deleteDirectory(localPath.toFile());
                } else {
                    throw e;
                }
            }
        }
    }

    private void pullRepositoryWithRetry(Path localPath,
            UsernamePasswordCredentialsProvider credentials, String repoName) {
        int attempt = 0;
        long delayMs = INITIAL_RETRY_DELAY_MS;

        while (true) {
            try {
                pullRepository(localPath, credentials);
                return;
            } catch (GitOperationException e) {
                attempt++;
                if (isRetryableError(e) && attempt < MAX_RETRIES) {
                    log.warn("Pull attempt {} failed for {} (retrying in {}ms): {}",
                            attempt, repoName, delayMs, e.getMessage());
                    sleepOrThrow(delayMs);
                    delayMs *= 2;
                } else {
                    throw e;
                }
            }
        }
    }

    private void cloneRepository(String repositoryUri, Path localPath,
                                  UsernamePasswordCredentialsProvider credentials) {
        try (Git ignored = Git.cloneRepository()
                .setURI(repositoryUri)
                .setDirectory(localPath.toFile())
                .setCredentialsProvider(credentials)
                .call()) {
            // clone complete
        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to clone repository: " + e.getMessage(), e);
        }
    }

    private void pullRepository(Path localPath, UsernamePasswordCredentialsProvider credentials) {
        try {
            Repository repository = new FileRepositoryBuilder()
                    .setGitDir(new File(localPath.toFile(), ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();

            try (Git git = new Git(repository)) {
                git.pull()
                        .setCredentialsProvider(credentials)
                        .call();
            }
        } catch (IOException e) {
            throw new GitOperationException("Failed to open repository: " + e.getMessage(), e);
        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to pull repository: " + e.getMessage(), e);
        }
    }

    // ---- Utility methods ----

    private String resolveGitRepoPath(Long exerciseId) {
        return harmoniaProperties.getProjects().stream()
                .filter(p -> p.getExerciseId().equals(exerciseId))
                .findFirst()
                .map(HarmoniaProperties.Project::getGitRepoPath)
                .orElse("Projects/exercise-" + exerciseId);
    }

    private boolean isRetryableError(GitOperationException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("503")
                || message.contains("502")
                || message.contains("504")
                || message.contains("Connection reset")
                || message.contains("Connection refused")
                || message.contains("Read timed out")
                || message.contains("Temporary failure");
    }

    private void sleepOrThrow(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new GitOperationException("Git operation interrupted", ie);
        }
    }

    private void deleteDirectory(File directory) {
        if (!directory.exists()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}

package de.tum.cit.aet.repositoryProcessing.service;

import de.tum.cit.aet.core.config.ArtemisConfig;
import de.tum.cit.aet.core.exceptions.GitOperationException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
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
 * Handles cloning and pulling repositories with retry logic.
 */
@Service
@Slf4j
public class GitOperationsService {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 2000; // 2 seconds

    @SuppressWarnings("unused")
    private final ArtemisConfig artemisConfig;

    @Autowired
    public GitOperationsService(ArtemisConfig artemisConfig) {
        this.artemisConfig = artemisConfig;
    }

    /**
     * Clones or pulls a repository using provided credentials (Dynamic).
     *
     * @param repositoryUri The URI of the Git repository
     * @param teamName      The name of the team
     * @param username      The username for authentication
     * @param password      The password (or token) for authentication
     * @param gitRepoPath   The base path where repositories should be stored
     * @return The local path where the repository is stored
     */
    public String cloneOrPullRepository(String repositoryUri, String teamName, String username, String password, String gitRepoPath) {
        return cloneOrPullRepository(repositoryUri, teamName, new UsernamePasswordCredentialsProvider(username, password), gitRepoPath);
    }

    private String cloneOrPullRepository(String repositoryUri, String teamName, UsernamePasswordCredentialsProvider credentialsProvider, String gitRepoPath) {
        // Extracts the repository name from the URI
        String[] parts = repositoryUri.split("/");
        String repoName = parts[parts.length - 1].replace(".git", "");

        Path localPath = Paths.get(gitRepoPath, repoName);
        File repoDir = localPath.toFile();

        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            log.info("Repository {} already exists, performing git pull", repoName);
            pullRepositoryWithRetry(localPath, credentialsProvider, repoName);
        } else {
            log.info("Cloning repository {} for team {}", repoName, teamName);
            cloneRepositoryWithRetry(repositoryUri, localPath, credentialsProvider, repoName);
        }

        return localPath.toString();
    }

    /**
     * Clones a repository with retry logic for transient errors.
     */
    private void cloneRepositoryWithRetry(String repositoryUri, Path localPath,
            UsernamePasswordCredentialsProvider credentialsProvider, String repoName) {
        int attempt = 0;
        long delayMs = INITIAL_RETRY_DELAY_MS;

        while (attempt < MAX_RETRIES) {
            try {
                cloneRepository(repositoryUri, localPath, credentialsProvider);
                return; // Success
            } catch (GitOperationException e) {
                attempt++;
                if (isRetryableError(e) && attempt < MAX_RETRIES) {
                    log.warn("Clone attempt {} failed for {} with retryable error, retrying in {}ms: {}",
                            attempt, repoName, delayMs, e.getMessage());
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new GitOperationException("Clone interrupted", ie);
                    }
                    delayMs *= 2; // Exponential backoff

                    // Clean up failed clone attempt
                    deleteDirectory(localPath.toFile());
                } else {
                    throw e; // Non-retryable or max retries exceeded
                }
            }
        }
    }

    /**
     * Pulls a repository with retry logic for transient errors.
     */
    private void pullRepositoryWithRetry(Path localPath,
            UsernamePasswordCredentialsProvider credentialsProvider, String repoName) {
        int attempt = 0;
        long delayMs = INITIAL_RETRY_DELAY_MS;

        while (attempt < MAX_RETRIES) {
            try {
                pullRepository(localPath, credentialsProvider);
                return; // Success
            } catch (GitOperationException e) {
                attempt++;
                if (isRetryableError(e) && attempt < MAX_RETRIES) {
                    log.warn("Pull attempt {} failed for {} with retryable error, retrying in {}ms: {}",
                            attempt, repoName, delayMs, e.getMessage());
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new GitOperationException("Pull interrupted", ie);
                    }
                    delayMs *= 2; // Exponential backoff
                } else {
                    throw e; // Non-retryable or max retries exceeded
                }
            }
        }
    }

    /**
     * Check if the error is retryable (transient network/server issues).
     */
    private boolean isRetryableError(GitOperationException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }

        // Check for common transient HTTP errors
        return message.contains("503") ||  // Service Unavailable
               message.contains("502") ||  // Bad Gateway
               message.contains("504") ||  // Gateway Timeout
               message.contains("Connection reset") ||
               message.contains("Connection refused") ||
               message.contains("Read timed out") ||
               message.contains("Temporary failure");
    }

    /**
     * Delete a directory recursively.
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
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

    /**
     * Clones a repository from the given URI to the local path.
     *
     * @param repositoryUri       The URI of the Git repository
     * @param localPath           The local path where the repository should be cloned
     * @param credentialsProvider The credentials provider
     */
    @SuppressWarnings("unused")
    private void cloneRepository(String repositoryUri, Path localPath, UsernamePasswordCredentialsProvider credentialsProvider) {
        try (Git git = Git.cloneRepository()
                .setURI(repositoryUri)
                .setDirectory(localPath.toFile())
                .setCredentialsProvider(credentialsProvider)
                .call()) {
            log.info("Successfully cloned repository to {}", localPath);
        } catch (GitAPIException e) {
            log.error("Failed to clone repository from {} to {}. Error: {}", repositoryUri, localPath, e.getMessage(), e);
            throw new GitOperationException("Failed to clone repository: " + e.getMessage(), e);
        }
    }

    /**
     * Pulls the latest changes for an existing repository.
     *
     * @param localPath           The local path of the repository
     * @param credentialsProvider The credentials provider
     */
    private void pullRepository(Path localPath, UsernamePasswordCredentialsProvider credentialsProvider) {
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder
                    .setGitDir(new File(localPath.toFile(), ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();

            try (Git git = new Git(repository)) {
                git.pull()
                        .setCredentialsProvider(credentialsProvider)
                        .call();

                log.info("Successfully pulled latest changes for {}", localPath);
            }
        } catch (IOException e) {
            // Handle IOException (e.g., .git directory not found or inaccessible)
            log.error("Failed to open repository at {}. Ensure it is a valid Git repository. Error: {}", localPath, e.getMessage(), e);
            throw new GitOperationException("Failed to open repository: " + e.getMessage(), e);
        } catch (GitAPIException e) {
            // Handle GitAPIException (e.g., authentication failure, network issue during pull)
            log.error("Failed to pull latest changes for {}. Error: {}", localPath, e.getMessage(), e);
            throw new GitOperationException("Failed to pull repository: " + e.getMessage(), e);
        }
    }
}

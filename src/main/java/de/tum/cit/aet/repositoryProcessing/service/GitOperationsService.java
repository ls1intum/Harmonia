package de.tum.cit.aet.repositoryProcessing.service;

import de.tum.cit.aet.core.config.ArtemisConfig;
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
 * Handles cloning and pulling repositories.
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
     * Clones or pulls a repository using provided credentials (Dynamic).
     *
     * @param repositoryUri The URI of the Git repository
     * @param teamName      The name of the team
     * @param username      The username for authentication
     * @param password      The password (or token) for authentication
     * @return The local path where the repository is stored
     */
    public String cloneOrPullRepository(String repositoryUri, String teamName, String username, String password) {
        return cloneOrPullRepository(repositoryUri, teamName, new UsernamePasswordCredentialsProvider(username, password));
    }

    private String cloneOrPullRepository(String repositoryUri, String teamName, UsernamePasswordCredentialsProvider credentialsProvider) {
        // Extracts the repository name from the URI
        String[] parts = repositoryUri.split("/");
        String repoName = parts[parts.length - 1].replace(".git", "");

        Path localPath = Paths.get(artemisConfig.getGitRepoPath(), repoName);
        File repoDir = localPath.toFile();

        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            log.info("Repository {} already exists, performing git pull", repoName);
            pullRepository(localPath, credentialsProvider);
        } else {
            log.info("Cloning repository {} for team {}", repoName, teamName);
            cloneRepository(repositoryUri, localPath, credentialsProvider);
        }

        return localPath.toString();
    }

    /**
     * Clones a repository from the given URI to the local path.
     *
     * @param repositoryUri       The URI of the Git repository
     * @param localPath           The local path where the repository should be cloned
     * @param credentialsProvider The credentials provider
     */
    private void cloneRepository(String repositoryUri, Path localPath, UsernamePasswordCredentialsProvider credentialsProvider) {
        try (Git git = Git.cloneRepository()
                .setURI(repositoryUri)
                .setDirectory(localPath.toFile())
                .setCredentialsProvider(credentialsProvider)
                .call()) {
            log.info("Successfully cloned repository to {}", localPath);
        } catch (GitAPIException e) {
            log.error("Failed to clone repository from {} to {}. Error: {}", repositoryUri, localPath, e.getMessage(), e);
            throw new RuntimeException("Failed to clone repository: " + e.getMessage(), e);
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
            throw new RuntimeException("Failed to open repository: " + e.getMessage(), e);
        } catch (GitAPIException e) {
            // Handle GitAPIException (e.g., authentication failure, network issue during pull)
            log.error("Failed to pull latest changes for {}. Error: {}", localPath, e.getMessage(), e);
            throw new RuntimeException("Failed to pull repository: " + e.getMessage(), e);
        }
    }
}

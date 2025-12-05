package de.tum.cit.aet.analysis.service;

import de.tum.cit.aet.repositoryProcessing.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GitContributionAnalysisService {

    public final Map<Long, int[]> authorContributions = new HashMap<>();
    // Regex to parse the output of 'git show --numstat <commit-hash>'
    // Example line: "12\t4\tsrc/main/java/Class.java" (lines added \t lines deleted \t filename)
    private static final Pattern NUMSTAT_PATTERN = Pattern.compile("^(\\d+)\\s+(\\d+)\\s+.*");

    /**
     * Maps commit hashes from VCS logs to the corresponding participant's email.
     */
    private Map<String, Long> mapCommitToAuthor(TeamRepositoryDTO repo) {
        Map<String, Long> commitToStudent = new HashMap<>();
        Map<String, Long> emailToStudent = new HashMap<>();
        repo.participation().team().students().forEach(student -> emailToStudent.put(student.email(), student.id()));
        for (VCSLogDTO log : repo.vcsLogs()) {
            commitToStudent.put(log.commitHash(), emailToStudent.get(log.email()));

        }
        return commitToStudent;
    }

    /**
     * Calculates the total lines added and removed for each author in a single repository
     * by running the 'git show --numstat' command for each commit.
     *
     * @param localPath      The local path to the repository on the file system.
     * @param commitToAuthor A map from commit hash (String) to Author ID (Long).
     * @throws IOException If an I/O error occurs during process execution.
     */
    public void analyzeRepositoryContributions(String localPath, Map<String, Long> commitToAuthor) throws IOException {
        System.out.println("Running git analysis on local path: " + localPath);

        // Iterate through all commits provided in the DTO
        for (String commitHash : commitToAuthor.keySet()) {
            Long authorId = commitToAuthor.get(commitHash);

            // Execute: git show --numstat <commit-hash>
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "-C", localPath, "show", "--numstat", commitHash
            );

            try {
                Process p = pb.start();

                // Read the output of the git command
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                int linesAdded = 0;
                int linesRemoved = 0;

                while ((line = reader.readLine()) != null) {
                    Matcher matcher = NUMSTAT_PATTERN.matcher(line);

                    if (matcher.find()) {
                        // Group 1 is lines added, Group 2 is lines removed
                        try {
                            linesAdded += Integer.parseInt(matcher.group(1));
                            linesRemoved += Integer.parseInt(matcher.group(2));
                        } catch (NumberFormatException e) {
                            // Handle cases where numstat output might be non-standard (e.g., '-\t-\tfilename')
                            System.err.println("Non-numeric line stat encountered: " + line);
                        }
                    }
                }

                // Wait for the process to finish
                int exitCode = p.waitFor();
                if (exitCode != 0) {
                    // Log error if git command failed
                    System.err.println("Git command failed for commit " + commitHash + " with exit code " + exitCode);
                }

                // Update the authorContributions map
                // [0] = linesAdded, [1] = linesRemoved
                int[] currentContributions = authorContributions.getOrDefault(authorId, new int[]{0, 0});
                currentContributions[0] += linesAdded;
                currentContributions[1] += linesRemoved;
                authorContributions.put(authorId, currentContributions);

                log.info("Student ID {}: +{} -{} lines for commit {}", authorId, linesAdded, linesRemoved, commitHash);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Git command interrupted", e);
            } catch (Exception e) {
                throw new IOException("Error executing git command for commit " + commitHash, e);
            }
        }

    }

    /**
     * Main method to orchestrate the analysis.
     *
     * @param teamRepositories The list of DTOs containing participation and logs.
     * @return A map of student email to their total lines added and removed [added, removed].
     */
    public Map<Long, int[]> processAllRepositories(List<TeamRepositoryDTO> teamRepositories) {

        for (TeamRepositoryDTO repo : teamRepositories) {
            // 1. Map commits to student emails (based on logs)
            Map<String, Long> commitToAuthor = mapCommitToAuthor(repo);

            // 2. Iterate and analyze each repository
            String localPath = repo.localPath();
            try {
                // The results are accumulated into the authorContributions map
                analyzeRepositoryContributions(localPath, commitToAuthor);
            } catch (IOException e) {
                System.err.println("Error processing repository " + repo.participation().repositoryUri() + ": " + e.getMessage());
                // Handle or log error
            }
        }
        return authorContributions;
    }
}

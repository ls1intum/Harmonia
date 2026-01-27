package de.tum.cit.aet.analysis.domain;

import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for storing AI-analyzed commit chunks.
 * Each chunk represents one or more commits analyzed together by the AI.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "analyzed_chunks")
public class AnalyzedChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "chunk_id", nullable = false)
    private UUID chunkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participation_id", nullable = false)
    private TeamParticipation participation;

    @Column(name = "chunk_identifier")
    private String chunkIdentifier;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "classification")
    private String classification;

    @Column(name = "effort_score")
    private Double effortScore;

    @Column(name = "complexity")
    private Double complexity;

    @Column(name = "novelty")
    private Double novelty;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "commit_shas", columnDefinition = "TEXT")
    private String commitShas; // Stored as comma-separated

    @Column(name = "commit_messages", columnDefinition = "TEXT")
    private String commitMessages; // Stored as JSON array

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "lines_changed")
    private Integer linesChanged;

    @Column(name = "is_bundled")
    private Boolean isBundled;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "total_chunks")
    private Integer totalChunks;

    @Column(name = "is_error")
    private Boolean isError;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Transient field to mark chunks from external contributors (not persisted).
     * External chunks are shown in UI but not included in CQI calculation.
     */
    @Transient
    private Boolean isExternalContributor;

    public AnalyzedChunk(TeamParticipation participation, String chunkIdentifier,
            String authorEmail, String authorName, String classification,
            Double effortScore, Double complexity, Double novelty, Double confidence,
            String reasoning, String commitShas,
            String commitMessages, LocalDateTime timestamp,
            Integer linesChanged, Boolean isBundled,
            Integer chunkIndex, Integer totalChunks,
            Boolean isError, String errorMessage,
            Boolean isExternalContributor) {
        this.participation = participation;
        this.chunkIdentifier = chunkIdentifier;
        this.authorEmail = authorEmail;
        this.authorName = authorName;
        this.classification = classification;
        this.effortScore = effortScore;
        this.complexity = complexity;
        this.novelty = novelty;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.commitShas = commitShas;
        this.commitMessages = commitMessages;
        this.timestamp = timestamp;
        this.linesChanged = linesChanged;
        this.isBundled = isBundled;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.isError = isError;
        this.errorMessage = errorMessage;
        this.isExternalContributor = isExternalContributor;
    }
    
    /**
     * Legacy constructor for backward compatibility.
     */
    public AnalyzedChunk(TeamParticipation participation, String chunkIdentifier,
            String authorEmail, String authorName, String classification,
            Double effortScore, Double complexity, Double novelty, Double confidence,
            String reasoning, String commitShas,
            String commitMessages, LocalDateTime timestamp,
            Integer linesChanged, Boolean isBundled,
            Integer chunkIndex, Integer totalChunks,
            Boolean isError, String errorMessage) {
        this(participation, chunkIdentifier, authorEmail, authorName, classification,
                effortScore, complexity, novelty, confidence, reasoning, commitShas,
                commitMessages, timestamp, linesChanged, isBundled, chunkIndex, totalChunks,
                isError, errorMessage, false);
    }
}

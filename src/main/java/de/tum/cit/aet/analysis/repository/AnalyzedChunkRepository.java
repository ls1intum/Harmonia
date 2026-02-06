package de.tum.cit.aet.analysis.repository;

import de.tum.cit.aet.analysis.domain.AnalyzedChunk;
import de.tum.cit.aet.repositoryProcessing.domain.TeamParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for persisting AI-analyzed commit chunks.
 */
@Repository
public interface AnalyzedChunkRepository extends JpaRepository<AnalyzedChunk, UUID> {

    /**
     * Find all chunks for a given team participation.
     *
     * @param participation the team participation to find chunks for
     * @return a list of analyzed chunks
     */
    List<AnalyzedChunk> findByParticipation(TeamParticipation participation);

    /**
     * Delete all chunks for a given participation.
     *
     * @param participation the team participation to delete chunks for
     */
    void deleteByParticipation(TeamParticipation participation);

    /**
     * Delete all chunks for a given participation (alias).
     *
     * @param participation the team participation to delete chunks for
     */
    void deleteAllByParticipation(TeamParticipation participation);
}

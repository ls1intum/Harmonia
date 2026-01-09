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
     */
    List<AnalyzedChunk> findByParticipation(TeamParticipation participation);

    /**
     * Delete all chunks for a given participation.
     */
    void deleteByParticipation(TeamParticipation participation);
}

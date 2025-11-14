package de.tum.cit.aet.analysis.repository;

import de.tum.cit.aet.analysis.domain.TeamAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AnalysisRepository extends JpaRepository<TeamAnalysis, UUID> {
}

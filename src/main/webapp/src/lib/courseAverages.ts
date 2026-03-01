import type { ClientResponseDTO } from '@/app/generated';
import { computeBasicMetrics } from '@/lib/utils';

export interface CourseAverages {
  avgCQI: number;
  avgCommits: number;
  avgLines: number;
  suspiciousPercentage: number;
  totalTeams: number;
  analyzedTeams: number;
  gitAnalyzedTeams: number;
}

export function computeCourseAverages(teams: ClientResponseDTO[]): CourseAverages | null {
  if (teams.length === 0) return null;

  const teamsWithCQI = teams.filter(team => team.cqi !== undefined);
  const totalCQI = teamsWithCQI.reduce((sum, team) => sum + (team.cqi ?? 0), 0);

  const teamsWithGitMetrics = teams.filter(
    team => team.analysisStatus === 'GIT_DONE' || team.analysisStatus === 'AI_ANALYZING' || team.analysisStatus === 'DONE',
  );
  const totalCommits = teamsWithGitMetrics.reduce((sum, team) => sum + computeBasicMetrics(team.students).totalCommits, 0);
  const totalLines = teamsWithGitMetrics.reduce((sum, team) => sum + computeBasicMetrics(team.students).totalLines, 0);
  const suspiciousCount = teams.filter(team => team.isSuspicious === true).length;

  return {
    avgCQI: teamsWithCQI.length > 0 ? Math.round(totalCQI / teamsWithCQI.length) : 0,
    avgCommits: teamsWithGitMetrics.length > 0 ? Math.round(totalCommits / teamsWithGitMetrics.length) : 0,
    avgLines: teamsWithGitMetrics.length > 0 ? Math.round(totalLines / teamsWithGitMetrics.length) : 0,
    suspiciousPercentage: Math.round((suspiciousCount / teams.length) * 100),
    totalTeams: teams.length,
    analyzedTeams: teamsWithCQI.length,
    gitAnalyzedTeams: teamsWithGitMetrics.length,
  };
}

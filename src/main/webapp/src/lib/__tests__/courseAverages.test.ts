import { describe, it, expect } from 'vitest';
import { computeCourseAverages } from '../courseAverages';
import type { Team } from '@/types/team';

const makeTeam = (overrides: Partial<Team> = {}): Team => ({
  id: '1',
  teamName: 'Team 1',
  tutor: 'Tutor',
  students: [],
  ...overrides,
});

describe('computeCourseAverages', () => {
  it('returns null for empty teams array', () => {
    expect(computeCourseAverages([])).toBeNull();
  });

  it('only teams with CQI contribute to avgCQI', () => {
    const teams: Team[] = [
      makeTeam({ id: '1', cqi: 80, analysisStatus: 'DONE' }),
      makeTeam({ id: '2', cqi: 60, analysisStatus: 'DONE' }),
      makeTeam({ id: '3', analysisStatus: 'PENDING' }), // no CQI
    ];
    const result = computeCourseAverages(teams)!;
    expect(result.avgCQI).toBe(70); // (80+60)/2
    expect(result.analyzedTeams).toBe(2);
  });

  it('only teams with GIT_DONE/AI_ANALYZING/DONE contribute to avgCommits/avgLines', () => {
    const teams: Team[] = [
      makeTeam({ id: '1', analysisStatus: 'DONE', basicMetrics: { totalCommits: 100, totalLines: 500 } }),
      makeTeam({ id: '2', analysisStatus: 'GIT_DONE', basicMetrics: { totalCommits: 50, totalLines: 300 } }),
      makeTeam({ id: '3', analysisStatus: 'AI_ANALYZING', basicMetrics: { totalCommits: 80, totalLines: 400 } }),
      makeTeam({ id: '4', analysisStatus: 'PENDING' }), // not counted
    ];
    const result = computeCourseAverages(teams)!;
    expect(result.avgCommits).toBe(77); // Math.round((100+50+80)/3)
    expect(result.avgLines).toBe(400); // Math.round((500+300+400)/3)
    expect(result.gitAnalyzedTeams).toBe(3);
  });

  it('calculates suspicious percentage correctly', () => {
    const teams: Team[] = [
      makeTeam({ id: '1', isSuspicious: true, analysisStatus: 'DONE' }),
      makeTeam({ id: '2', isSuspicious: false, analysisStatus: 'DONE' }),
      makeTeam({ id: '3', isSuspicious: false, analysisStatus: 'DONE' }),
      makeTeam({ id: '4', isSuspicious: true, analysisStatus: 'DONE' }),
    ];
    const result = computeCourseAverages(teams)!;
    expect(result.suspiciousPercentage).toBe(50);
    expect(result.totalTeams).toBe(4);
  });

  it('handles partial analysis with correct counts', () => {
    const teams: Team[] = [
      makeTeam({ id: '1', cqi: 90, analysisStatus: 'DONE', basicMetrics: { totalCommits: 120, totalLines: 600 } }),
      makeTeam({ id: '2', analysisStatus: 'GIT_DONE', basicMetrics: { totalCommits: 60, totalLines: 200 } }),
      makeTeam({ id: '3', analysisStatus: 'PENDING' }),
      makeTeam({ id: '4', analysisStatus: 'GIT_ANALYZING' }),
    ];
    const result = computeCourseAverages(teams)!;
    expect(result.totalTeams).toBe(4);
    expect(result.analyzedTeams).toBe(1); // only DONE with CQI
    expect(result.gitAnalyzedTeams).toBe(2); // DONE + GIT_DONE
    expect(result.avgCQI).toBe(90);
    expect(result.avgCommits).toBe(90); // (120+60)/2
    expect(result.avgLines).toBe(400); // (600+200)/2
  });
});

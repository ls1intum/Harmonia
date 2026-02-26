import { describe, it, expect } from 'vitest';
import { computeCourseAverages } from '../courseAverages';
import type { ClientResponseDTO } from '@/app/generated';

const makeTeam = (overrides: Partial<ClientResponseDTO> = {}): ClientResponseDTO => ({
  teamId: 1,
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
    const teams: ClientResponseDTO[] = [
      makeTeam({ teamId: 1, cqi: 80, analysisStatus: 'DONE' }),
      makeTeam({ teamId: 2, cqi: 60, analysisStatus: 'DONE' }),
      makeTeam({ teamId: 3, analysisStatus: 'PENDING' }), // no CQI
    ];
    const result = computeCourseAverages(teams)!;
    expect(result.avgCQI).toBe(70); // (80+60)/2
    expect(result.analyzedTeams).toBe(2);
  });

  it('only teams with GIT_DONE/AI_ANALYZING/DONE contribute to avgCommits/avgLines', () => {
    const teams: ClientResponseDTO[] = [
      makeTeam({
        teamId: 1,
        analysisStatus: 'DONE',
        students: [
          { name: 'A', commitCount: 60, linesAdded: 300 },
          { name: 'B', commitCount: 40, linesAdded: 200 },
        ],
      }),
      makeTeam({
        teamId: 2,
        analysisStatus: 'GIT_DONE',
        students: [
          { name: 'C', commitCount: 30, linesAdded: 200 },
          { name: 'D', commitCount: 20, linesAdded: 100 },
        ],
      }),
      makeTeam({
        teamId: 3,
        analysisStatus: 'AI_ANALYZING',
        students: [
          { name: 'E', commitCount: 50, linesAdded: 250 },
          { name: 'F', commitCount: 30, linesAdded: 150 },
        ],
      }),
      makeTeam({ teamId: 4, analysisStatus: 'PENDING' }), // not counted
    ];
    const result = computeCourseAverages(teams)!;
    expect(result.avgCommits).toBe(77); // Math.round((100+50+80)/3)
    expect(result.avgLines).toBe(400); // Math.round((500+300+400)/3)
    expect(result.gitAnalyzedTeams).toBe(3);
  });

  it('calculates suspicious percentage correctly', () => {
    const teams: ClientResponseDTO[] = [
      makeTeam({ teamId: 1, isSuspicious: true, analysisStatus: 'DONE' }),
      makeTeam({ teamId: 2, isSuspicious: false, analysisStatus: 'DONE' }),
      makeTeam({ teamId: 3, isSuspicious: false, analysisStatus: 'DONE' }),
      makeTeam({ teamId: 4, isSuspicious: true, analysisStatus: 'DONE' }),
    ];
    const result = computeCourseAverages(teams)!;
    expect(result.suspiciousPercentage).toBe(50);
    expect(result.totalTeams).toBe(4);
  });

  it('handles partial analysis with correct counts', () => {
    const teams: ClientResponseDTO[] = [
      makeTeam({
        teamId: 1,
        cqi: 90,
        analysisStatus: 'DONE',
        students: [
          { name: 'A', commitCount: 80, linesAdded: 400 },
          { name: 'B', commitCount: 40, linesAdded: 200 },
        ],
      }),
      makeTeam({
        teamId: 2,
        analysisStatus: 'GIT_DONE',
        students: [
          { name: 'C', commitCount: 40, linesAdded: 150 },
          { name: 'D', commitCount: 20, linesAdded: 50 },
        ],
      }),
      makeTeam({ teamId: 3, analysisStatus: 'PENDING' }),
      makeTeam({ teamId: 4, analysisStatus: 'GIT_ANALYZING' }),
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

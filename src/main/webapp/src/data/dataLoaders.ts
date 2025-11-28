import type { Team } from '@/types/team';
import { dummyTeams } from '@/data/dummyTeams';
import config from '@/config';
import { RequestResourceApi, type TeamRepositoryDTO } from '@/app/generated';
import { Configuration } from '@/app/generated/configuration';

// ============================================================
// CONFIGURATION - Toggle between real API and dummy data
// ============================================================
const USE_DUMMY_DATA = config.USE_DUMMY_DATA;

// Initialize API client with Basic Auth
const apiConfig = new Configuration({
  basePath: 'http://localhost:8080',
  username: 'admin',
  password: 'admin1234',
  baseOptions: {
    auth: {
      username: 'admin',
      password: 'admin1234',
    },
  },
});
const requestApi = new RequestResourceApi(apiConfig);

// ============================================================
// TYPES
// ============================================================
export type BasicTeamData = Omit<Team, 'cqi' | 'isSuspicious' | 'subMetrics'>;
export type ComplexTeamData = Team;

// ============================================================
// DUMMY DATA HELPERS
// ============================================================
function getBasicDummyTeams(): BasicTeamData[] {
  return dummyTeams.map(team => {
    // TODO: Potentially rework
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { cqi, isSuspicious, subMetrics, ...basicData } = team;
    return basicData;
  });
}

function getComplexDummyTeams(): ComplexTeamData[] {
  return dummyTeams;
}

// Simulate API delay
function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// ============================================================
// DATA TRANSFORMATION - Convert DTO to Frontend Types
// ============================================================

/**
 * Transform TeamRepositoryDTO to BasicTeamData with mocked analysis
 */
function transformToBasicTeamData(dto: TeamRepositoryDTO): BasicTeamData {
  const teamName = dto.participation?.team?.name || 'Unknown Team';
  const students = dto.participation?.team?.students || [];
  const totalCommits = dto.commitCount || 0;

  // Mock: Distribute commits among students
  const studentData = students.map((student, index) => {
    // Generate random but realistic commit distribution
    const ratio = 0.4 + Math.random() * 0.2; // Between 40-60%
    const commits = index === 0 ? Math.floor(totalCommits * ratio) : totalCommits - Math.floor(totalCommits * ratio);

    // Mock: Calculate lines based on commits (15-25 lines per commit)
    const linesPerCommit = 15 + Math.random() * 10;
    const linesAdded = Math.floor(commits * linesPerCommit);

    return {
      name: student.name || 'Unknown',
      commits,
      linesAdded,
    };
  });

  // Mock: Calculate total lines
  const totalLines = studentData.reduce((sum, s) => sum + (s.linesAdded || 0), 0);

  return {
    id: dto.participation?.id?.toString() || 'unknown',
    teamName,
    students: studentData,
    basicMetrics: {
      totalCommits,
      totalLines,
    },
  };
}

/**
 * Transform TeamRepositoryDTO to ComplexTeamData with mocked CQI analysis
 */
function transformToComplexTeamData(dto: TeamRepositoryDTO): ComplexTeamData {
  const basicData = transformToBasicTeamData(dto);
  const totalCommits = dto.commitCount || 0;

  // Mock: Calculate CQI score (0-100)
  // Formula: Base score + commit bonus, capped at 100
  const baseScore = 50;
  const commitBonus = Math.min(40, totalCommits * 0.3);
  const randomVariation = Math.random() * 20 - 10; // ±10 points
  const cqi = Math.max(0, Math.min(100, Math.floor(baseScore + commitBonus + randomVariation)));

  // Mock: Generate sub-metrics (placeholder values)
  const subMetrics = [
    {
      name: 'Contribution Balance',
      value: 85 + Math.floor(Math.random() * 15),
      weight: 30,
      description: 'Are teammates contributing at similar levels?',
      details: 'Mock data - real analysis not yet implemented.',
    },
    {
      name: 'Pairing Signals',
      value: 75 + Math.floor(Math.random() * 20),
      weight: 30,
      description: 'Did teammates actually work together?',
      details: 'Mock data - real analysis not yet implemented.',
    },
    {
      name: 'Ownership Distribution',
      value: 80 + Math.floor(Math.random() * 15),
      weight: 20,
      description: 'Are key files shared rather than monopolized?',
      details: 'Mock data - real analysis not yet implemented.',
    },
    {
      name: 'Quality Hygiene at HEAD',
      value: 70 + Math.floor(Math.random() * 20),
      weight: 10,
      description: 'Basic quality standards met?',
      details: 'Mock data - real analysis not yet implemented.',
    },
  ];

  return {
    ...basicData,
    cqi,
    isSuspicious: false, // Always normal for now
    subMetrics,
  };
}

// ============================================================
// API CALLS (Real Implementation)
// ============================================================
// TODO: Use course and exercise
// eslint-disable-next-line @typescript-eslint/no-unused-vars
async function fetchBasicTeamsFromAPI(_course: string, _exercise: string): Promise<BasicTeamData[]> {
  // Note: course and exercise parameters not yet used by backend endpoint
  try {
    const response = await requestApi.fetchAndCloneRepositories();
    const teamRepos = response.data;

    // Transform DTOs to BasicTeamData
    return teamRepos.map(transformToBasicTeamData);
  } catch (error) {
    console.error('Error fetching basic team data:', error);
    throw error;
  }
}

// TODO: Use course and exercise
// eslint-disable-next-line @typescript-eslint/no-unused-vars
async function fetchComplexTeamsFromAPI(_course: string, _exercise: string): Promise<ComplexTeamData[]> {
  // Note: course and exercise parameters not yet used by backend endpoint
  try {
    const response = await requestApi.fetchAndCloneRepositories();
    const teamRepos = response.data;

    // Transform DTOs to ComplexTeamData with mocked analysis
    return teamRepos.map(transformToComplexTeamData);
  } catch (error) {
    console.error('Error fetching complex team data:', error);
    throw error;
  }
}

async function fetchTeamByIdFromAPI(teamId: string): Promise<ComplexTeamData | null> {
  try {
    const response = await requestApi.fetchAndCloneRepositories();
    const teamRepos = response.data;

    // Find the team by ID
    const teamRepo = teamRepos.find((repo: TeamRepositoryDTO) => repo.participation?.id?.toString() === teamId);

    if (!teamRepo) {
      return null;
    }

    return transformToComplexTeamData(teamRepo);
  } catch (error) {
    console.error('Error fetching team by ID:', error);
    throw error;
  }
}

// ============================================================
// PUBLIC API - Data Loaders
// ============================================================

/**
 * Fetch basic team data (quick, partial information)
 */
export async function loadBasicTeamData(course: string, exercise: string): Promise<BasicTeamData[]> {
  if (USE_DUMMY_DATA) {
    await delay(500); // Simulate network delay
    return getBasicDummyTeams();
  }

  return fetchBasicTeamsFromAPI(course, exercise);
}

/**
 * Fetch complex team data (slower, complete analysis with CQI, etc.)
 */
export async function loadComplexTeamData(course: string, exercise: string): Promise<ComplexTeamData[]> {
  if (USE_DUMMY_DATA) {
    await delay(2000); // Simulate longer processing time
    return getComplexDummyTeams();
  }

  return fetchComplexTeamsFromAPI(course, exercise);
}

/**
 * Fetch both basic and complex data concurrently
 * Returns a callback-based system for progressive updates
 */
export async function loadTeamDataProgressive(
  course: string,
  exercise: string,
  onBasicLoaded: (teams: BasicTeamData[]) => void,
  onComplexLoaded: (teams: ComplexTeamData[]) => void,
  onError: (error: Error) => void,
): Promise<void> {
  try {
    // Start both requests simultaneously
    const basicPromise = loadBasicTeamData(course, exercise);
    const complexPromise = loadComplexTeamData(course, exercise);

    // Handle basic data as soon as it's ready
    basicPromise.then(onBasicLoaded).catch(onError);

    // Handle complex data when it's ready
    complexPromise.then(onComplexLoaded).catch(onError);

    // Wait for both to complete
    await Promise.all([basicPromise, complexPromise]);
  } catch (error) {
    onError(error instanceof Error ? error : new Error('Unknown error'));
  }
}

/**
 * Fetch a single team by ID
 */
export async function loadTeamById(teamId: string): Promise<ComplexTeamData | null> {
  if (USE_DUMMY_DATA) {
    await delay(300); // Simulate network delay
    return dummyTeams.find(t => t.id === teamId) || null;
  }

  return fetchTeamByIdFromAPI(teamId);
}

/**
 * Trigger a re-computation/reanalysis
 */
export async function triggerReanalysis(course: string, exercise: string): Promise<void> {
  if (USE_DUMMY_DATA) {
    await delay(500);
    return;
  }

  // TODO: Implement actual reanalysis trigger
  const apiUrl = `/api/analysis/${course}/${exercise}/recompute`;

  const response = await fetch(apiUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`Reanalysis failed: ${response.statusText}`);
  }
}

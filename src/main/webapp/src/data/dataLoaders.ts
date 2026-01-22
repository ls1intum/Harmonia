import type { Team } from '@/types/team';
import { dummyTeams } from '@/data/dummyTeams';
import config from '@/config';
import { RequestResourceApi, type ClientResponseDTO } from '@/app/generated';
import { Configuration } from '@/app/generated/configuration';

// ============================================================
// CONFIGURATION - Toggle between real API and dummy data
// ============================================================
const USE_DUMMY_DATA = config.USE_DUMMY_DATA;

// Initialize API client
const apiConfig = new Configuration({
  basePath: window.location.origin,
  username: 'admin',
  password: 'admin1234',
  baseOptions: {
    auth: {
      username: 'admin',
      password: 'admin1234',
    },
    withCredentials: true, // Important: Send cookies (JWT, etc.) with requests
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
// CACHE - Store transformed team data to ensure consistency
// ============================================================
const teamCache = new Map<string, ComplexTeamData>();

function getCachedTeam(teamId: string): ComplexTeamData | undefined {
  return teamCache.get(teamId);
}

function setCachedTeam(teamId: string, team: ComplexTeamData): void {
  teamCache.set(teamId, team);
}

// ============================================================
// DATA TRANSFORMATION - Convert DTO to Client Types
// ============================================================

/**
 * Transform ClientResponseDTO to BasicTeamData with mocked analysis
 */
function transformToBasicTeamData(dto: ClientResponseDTO): BasicTeamData {
  const teamName = dto.teamName || 'Unknown Team';
  const students = dto.students || [];
  const totalCommits = dto.submissionCount || 0;

  const studentData = students.map(student => {
    const commits = student.commitCount || 0;
    const linesAdded = student.linesAdded || 0;
    const linesDeleted = student.linesDeleted || 0;
    const linesChanged = student.linesChanged || 0;

    return {
      name: student.name || 'Unknown',
      commits,
      linesAdded,
      linesDeleted,
      linesChanged,
    };
  });

  const totalLines = studentData.reduce((sum, s) => sum + (s.linesAdded || 0), 0);

  return {
    id: dto.teamId?.toString() || 'unknown',
    teamName,
    tutor: dto.tutor || 'Unassigned',
    students: studentData,
    basicMetrics: {
      totalCommits,
      totalLines,
    },
  };
}

/**
 * Transform ClientResponseDTO to ComplexTeamData using server-calculated CQI
 * Exported for use in components that need to transform DTOs
 */
export function transformToComplexTeamData(dto: ClientResponseDTO): ComplexTeamData {
  const basicData = transformToBasicTeamData(dto);
  const teamId = dto.teamId?.toString() || 'unknown';

  // Check if we already have this team cached to avoid re-transforming
  const cached = getCachedTeam(teamId);
  if (cached) {
    return cached;
  }

  const balanceScore = dto.balanceScore !== undefined && dto.balanceScore !== null ? dto.balanceScore : 0;
  const pairingScore = dto.pairingScore !== undefined && dto.pairingScore !== null ? dto.pairingScore : 0;
  const computedCqi = (balanceScore * 0.5) + (pairingScore * 0.5);
  const cqi = dto.cqi !== undefined && dto.cqi !== null ? dto.cqi : computedCqi;
  const isSuspicious = dto.isSuspicious ?? false;

  const subMetrics = [
    {
      name: 'Contribution Balance',
      value: Math.round(balanceScore),
      weight: 50,
      description: 'Are teammates contributing at similar levels?',
      details: `Score: ${Math.round(balanceScore)}/100. Calculated from commit distribution.`,
    },
    {
      name: 'Pair Programming Signals',
      value: Math.round(pairingScore),
      weight: 50,
      description: 'Did teammates actually work together?',
      details: `Score: ${Math.round(pairingScore)}/100. Calculated from alternation and co-editing patterns.`,
    },
  ];

  // Map analysis history from server
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const analysisHistory = (dto as any).analysisHistory?.map((chunk: any) => ({
    id: chunk.id,
    authorEmail: chunk.authorEmail,
    authorName: chunk.authorName,
    classification: chunk.classification,
    effortScore: chunk.effortScore,
    reasoning: chunk.reasoning,
    commitShas: chunk.commitShas || [],
    commitMessages: chunk.commitMessages || [],
    timestamp: chunk.timestamp,
    linesChanged: chunk.linesChanged,
    isBundled: chunk.isBundled,
    chunkIndex: chunk.chunkIndex,
    totalChunks: chunk.totalChunks,
    isError: chunk.isError,
    errorMessage: chunk.errorMessage,
  }));

  // Map orphan commits
  const orphanCommits = dto.orphanCommits?.map(commit => ({
    commitHash: commit.commitHash || '',
    authorEmail: commit.authorEmail || '',
    authorName: commit.authorName || '',
    message: commit.message || '',
    timestamp: commit.timestamp || new Date().toISOString(),
    linesAdded: commit.linesAdded || 0,
    linesDeleted: commit.linesDeleted || 0,
  }));

  const team: ComplexTeamData = {
    ...basicData,
    cqi: Math.round(cqi),
    isSuspicious,
    subMetrics,
    analysisHistory,
    orphanCommits,
  };

  // Cache the transformed team
  setCachedTeam(teamId, team);
  return team;
}

// ============================================================
// SERVER API CALLS
// ============================================================

// TODO: Use course and exercise parameters when server supports them
async function fetchBasicTeamsFromAPI(exerciseId: string): Promise<BasicTeamData[]> {
  try {
    const response = await requestApi.fetchData(parseInt(exerciseId));
    const teamRepos = response.data;

    // Transform DTOs to BasicTeamData
    return teamRepos.map(transformToBasicTeamData);
  } catch (error) {
    console.error('Error fetching basic team data:', error);
    throw error;
  }
}

// TODO: Use course and exercise parameters when server supports them
async function fetchComplexTeamsFromAPI(exerciseId: string): Promise<ComplexTeamData[]> {
  try {
    const response = await requestApi.fetchData(parseInt(exerciseId));
    const teamRepos = response.data;

    // Transform DTOs to ComplexTeamData with mocked analysis
    return teamRepos.map(transformToComplexTeamData);
  } catch (error) {
    console.error('Error fetching complex team data:', error);
    throw error;
  }
}

/**
 * Fetch a single team by ID from server
 */
async function fetchTeamByIdFromServer(teamId: string): Promise<ComplexTeamData | null> {
  try {
    const response = await requestApi.getData();
    const teamRepo = response.data.find((repo: ClientResponseDTO) => repo.teamId?.toString() === teamId);
    return teamRepo ? transformToComplexTeamData(teamRepo) : null;
  } catch (error) {
    console.error('Error fetching team by ID from server:', error);
    throw error;
  }
}

// ============================================================
// PUBLIC API - Data Loaders
// ============================================================

/**
 * Stream team data from server via SSE
 */
export function loadBasicTeamDataStream(
  exerciseId: string,
  onStart: (total: number) => void,
  onUpdate: (team: ComplexTeamData) => void,
  onComplete: () => void,
  onError: (error: unknown) => void,
): () => void {
  if (USE_DUMMY_DATA) {
    // Simulate streaming for dummy data
    const teams = getComplexDummyTeams();
    onStart(teams.length);
    let i = 0;
    const interval = setInterval(() => {
      if (i < teams.length) {
        onUpdate(teams[i]);
        i++;
      } else {
        clearInterval(interval);
        onComplete();
      }
    }, 200);
    return () => clearInterval(interval);
  }

  const eventSource = new EventSource(`/api/requestResource/stream?exerciseId=${exerciseId}`, {
    withCredentials: true,
  });

  eventSource.onmessage = event => {
    try {
      const data = JSON.parse(event.data);
      if (data.type === 'START') {
        onStart(data.total);
      } else if (data.type === 'UPDATE') {
        // Server sends ClientResponseDTO with CQI and isSuspicious, so transform to ComplexTeamData
        onUpdate(transformToComplexTeamData(data.data));
      } else if (data.type === 'DONE') {
        eventSource.close();
        onComplete();
      }
    } catch (e) {
      console.error('Error parsing SSE event:', e);
    }
  };

  eventSource.onerror = error => {
    console.error('SSE Error:', error);
    eventSource.close();
    onError(error);
  };

  return () => eventSource.close();
}

/**
 * Fetch basic team data (quick, partial information)
 */
export async function loadBasicTeamData(_course: string, exercise: string): Promise<BasicTeamData[]> {
  if (USE_DUMMY_DATA) {
    await delay(500); // Simulate network delay
    return getBasicDummyTeams();
  }

  return fetchBasicTeamsFromAPI(exercise);
}

/**
 * Fetch complex team data (slower, complete analysis with CQI, etc.)
 */
export async function loadComplexTeamData(_course: string, exercise: string): Promise<ComplexTeamData[]> {
  if (USE_DUMMY_DATA) {
    await delay(2000); // Simulate longer processing time
    return getComplexDummyTeams();
  }

  return fetchComplexTeamsFromAPI(exercise);
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
export async function loadTeamById(teamId: string, _exerciseId?: string): Promise<ComplexTeamData | null> {
  if (USE_DUMMY_DATA) {
    await delay(300); // Simulate network delay
    return dummyTeams.find(t => t.id === teamId) || null;
  }

  return fetchTeamByIdFromServer(teamId);
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

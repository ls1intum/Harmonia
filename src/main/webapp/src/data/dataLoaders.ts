import type { Team, SubMetric } from '@/types/team';
import { dummyTeams } from '@/data/dummyTeams';
import config from '@/config';
import { RequestResourceApi, type ClientResponseDTO, type CQIResultDTO } from '@/app/generated';
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

function getComplexDummyTeams(): Team[] {
  return dummyTeams;
}

// Simulate API delay
function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// ============================================================
// CACHE - Store transformed team data to ensure consistency
// ============================================================
const teamCache = new Map<string, Team>();

function setCachedTeam(teamId: string, team: Team): void {
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

  // Students are already in DTO format, just ensure defaults
  const studentData = students.map(student => ({
    name: student.name || 'Unknown',
    commitCount: student.commitCount || 0,
    linesAdded: student.linesAdded || 0,
    linesDeleted: student.linesDeleted || 0,
    linesChanged: student.linesChanged || 0,
  }));

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
 * Transform ClientResponseDTO to Team using server-calculated CQI
 */
export function transformToComplexTeamData(dto: ClientResponseDTO): Team {
  const basicData = transformToBasicTeamData(dto);
  const teamId = dto.teamId?.toString() || 'unknown';

  // Use CQI from server (calculated server-side)
  // Keep undefined if null - indicates analysis not yet complete
  const cqi = dto.cqi !== undefined && dto.cqi !== null ? Math.round(dto.cqi) : undefined;
  const isSuspicious = dto.isSuspicious ?? undefined;

  // Extract CQI details from server response
  const serverCqiDetails = dto.cqiDetails as CQIResultDTO | undefined;

  // Generate sub-metrics from CQI details if available, or undefined if CQI not calculated
  const subMetrics: SubMetric[] | undefined = serverCqiDetails?.components
    ? [
      {
        name: 'Effort Balance',
        value: Math.round(serverCqiDetails.components.effortBalance ?? 0),
        weight: 40,
        description: 'Is effort distributed fairly among team members?',
        details: 'Based on LLM-weighted contribution analysis. Higher scores indicate balanced workload distribution.',
      },
      {
        name: 'Lines of Code Balance',
        value: Math.round(serverCqiDetails.components.locBalance ?? 0),
        weight: 25,
        description: 'Are code contributions balanced?',
        details: 'Measures the distribution of lines added/deleted across team members.',
      },
      {
        name: 'Temporal Spread',
        value: Math.round(serverCqiDetails.components.temporalSpread ?? 0),
        weight: 20,
        description: 'Is work spread over time or crammed at deadline?',
        details: 'Higher scores mean work was spread consistently throughout the project period.',
      },
      {
        name: 'File Ownership Spread',
        value: Math.round(serverCqiDetails.components.ownershipSpread ?? 0),
        weight: 15,
        description: 'Are files owned by multiple team members?',
        details: 'Measures how well files are shared among team members (based on git blame analysis).',
      },
    ]
    : cqi !== undefined
      ? [
        {
          name: 'Contribution Balance',
          value: cqi,
          weight: 40,
          description: 'Are teammates contributing at similar levels?',
          details: 'Calculated from commit distribution.',
        },
        {
          name: 'Ownership Distribution',
          value: 0,
          weight: 30,
          description: 'Are key files shared rather than monopolized?',
          details: 'Calculated from git blame analysis.',
        },
        {
          name: 'Pairing Signals',
          value: 0,
          weight: 30,
          description: 'Did teammates actually work together?',
          details: 'Not yet implemented.',
        },
      ]
      : undefined;

  // Use analysis history directly from server (already in correct DTO format)
  const analysisHistory = dto.analysisHistory;

  // Use orphan commits directly from server (already in correct DTO format)
  const orphanCommits = dto.orphanCommits;

  const team: Team = {
    ...basicData,
    cqi,
    isSuspicious,
    cqiDetails: serverCqiDetails,
    subMetrics,
    analysisHistory,
    orphanCommits,
    analysisStatus: (dto as any).analysisStatus,
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
    const response = await fetch(`/api/requestResource/${exerciseId}/getData`, {
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
    });
    if (!response.ok) throw new Error(`Failed to fetch teams: ${response.statusText}`);
    const teamRepos: ClientResponseDTO[] = await response.json();

    // Transform DTOs to BasicTeamData
    return teamRepos.map(transformToBasicTeamData);
  } catch (error) {
    console.error('Error fetching basic team data:', error);
    throw error;
  }
}

// TODO: Use course and exercise parameters when server supports them
async function fetchComplexTeamsFromAPI(exerciseId: string): Promise<Team[]> {
  try {
    const response = await fetch(`/api/requestResource/${exerciseId}/getData`, {
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
    });
    if (!response.ok) throw new Error(`Failed to fetch teams: ${response.statusText}`);
    const teamRepos: ClientResponseDTO[] = await response.json();

    // Transform DTOs to Team with mocked analysis
    return teamRepos.map(transformToComplexTeamData);
  } catch (error) {
    console.error('Error fetching complex team data:', error);
    throw error;
  }
}

/**
 * Fetch a single team by ID from server
 */
async function fetchTeamByIdFromServer(teamId: string, exerciseId?: string): Promise<Team | null> {
  try {
    // Use exercise-specific endpoint if exerciseId is provided
    const url = exerciseId
      ? `/api/requestResource/${exerciseId}/getData`
      : `/api/requestResource/getData`;
    const response = await fetch(url, {
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
      },
    });
    const data: ClientResponseDTO[] = await response.json();
    const teamRepo = data.find((repo: ClientResponseDTO) => repo.teamId?.toString() === teamId);
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
 * The streaming flow is:
 * 1. START - total count of teams
 * 2. INIT - each team with pending status (no CQI)
 * 3. UPDATE - each team with analyzed data (with CQI)
 * 4. DONE - analysis complete
 */
export function loadBasicTeamDataStream(
  exerciseId: string,
  onStart: (total: number) => void,
  onInit: (team: Team) => void,
  onUpdate: (team: Team) => void,
  onComplete: () => void,
  onError: (error: unknown) => void,
): () => void {
  if (USE_DUMMY_DATA) {
    // Simulate streaming for dummy data
    const teams = getComplexDummyTeams();
    onStart(teams.length);
    // First send all as pending
    for (const team of teams) {
      onInit({ ...team, cqi: undefined, isSuspicious: undefined });
    }
    // Then simulate analysis updates
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
      } else if (data.type === 'INIT') {
        // Teams arriving with pending status (no CQI yet)
        onInit(transformToComplexTeamData(data.data));
      } else if (data.type === 'UPDATE') {
        // Server sends ClientResponseDTO with CQI and isSuspicious, so transform to Team
        onUpdate(transformToComplexTeamData(data.data));
      } else if (data.type === 'DONE') {
        eventSource.close();
        onComplete();
      }
    } catch (e) {
      console.error('Error parsing SSE event:', e);
      onError(e);
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
export async function loadComplexTeamData(_course: string, exercise: string): Promise<Team[]> {
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
  onComplexLoaded: (teams: Team[]) => void,
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
export async function loadTeamById(teamId: string, _exerciseId?: string): Promise<Team | null> {
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

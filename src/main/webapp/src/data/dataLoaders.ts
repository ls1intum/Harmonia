import type { Team } from '@/types/team';
import { dummyTeams } from '@/data/dummyTeams';
import config from '@/config';

// ============================================================
// CONFIGURATION - Toggle between real API and dummy data
// ============================================================
const USE_DUMMY_DATA = config.USE_DUMMY_DATA;

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
// API CALLS (Real Implementation)
// ============================================================
async function fetchBasicTeamsFromAPI(course: string, exercise: string): Promise<BasicTeamData[]> {
  // TODO: Replace with actual API endpoint
  const apiUrl = `/api/analysis/${course}/${exercise}/basic`;

  try {
    const response = await fetch(apiUrl, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        // TODO: Add authentication headers if needed
        // 'Authorization': `Bearer ${token}`,
      },
    });

    if (!response.ok) {
      console.log(`API request failed: ${response.statusText}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Error fetching basic team data:', error);
    throw error;
  }
}

async function fetchComplexTeamsFromAPI(course: string, exercise: string): Promise<ComplexTeamData[]> {
  // TODO: Replace with actual API endpoint
  const apiUrl = `/api/analysis/${course}/${exercise}/complex`;

  try {
    const response = await fetch(apiUrl, {
      method: 'POST', // Assuming complex analysis might be a POST request
      headers: {
        'Content-Type': 'application/json',
        // TODO: Add authentication headers if needed
      },
      body: JSON.stringify({
        course,
        exercise,
        // TODO: Add any additional parameters needed for analysis
      }),
    });

    if (!response.ok) {
      console.log(`API request failed: ${response.statusText}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Error fetching complex team data:', error);
    throw error;
  }
}

async function fetchTeamByIdFromAPI(teamId: string): Promise<ComplexTeamData | null> {
  // TODO: Replace with actual API endpoint
  const apiUrl = `/api/teams/${teamId}`;

  try {
    const response = await fetch(apiUrl, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      if (response.status === 404) {
        return null;
      }
      console.log(`API request failed: ${response.statusText}`);
    }

    return await response.json();
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

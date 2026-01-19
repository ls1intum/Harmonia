import { useQuery } from '@tanstack/react-query';
import { AnalysisResourceApi, Configuration } from '@/app/generated';
import type { AnalysisStatusDTO } from '@/app/generated';
import type { AnalysisStatus } from '@/components/ActivityLog';

// Initialize API client
const apiConfig = new Configuration({
  basePath: window.location.origin,
  baseOptions: {
    withCredentials: true,
  },
});
const analysisApi = new AnalysisResourceApi(apiConfig);

/**
 * Fetches analysis status from the server using generated API.
 */
async function fetchAnalysisStatus(exerciseId: string): Promise<AnalysisStatus> {
  const response = await analysisApi.getStatus(parseInt(exerciseId));
  const data = response.data;
  
  // Validate and cast state to the expected type
  const state = data.state as AnalysisStatus['state'];
  const validStates: AnalysisStatus['state'][] = ['IDLE', 'RUNNING', 'DONE', 'ERROR'];
  const finalState = validStates.includes(state) ? state : 'IDLE';
  
  return {
    state: finalState,
    totalTeams: data.totalTeams || 0,
    processedTeams: data.processedTeams || 0,
    currentTeamName: data.currentTeamName,
    currentStage: data.currentStage,
    errorMessage: data.errorMessage,
  };
}

/**
 * Cancels a running analysis using generated API.
 */
export async function cancelAnalysis(exerciseId: string): Promise<AnalysisStatusDTO> {
  const response = await analysisApi.cancelAnalysis(parseInt(exerciseId));
  return response.data;
}

/**
 * Clears data for an exercise using generated API.
 */
export async function clearData(exerciseId: string, type: 'db' | 'files' | 'both'): Promise<void> {
  await analysisApi.clearData(parseInt(exerciseId), type);
}

interface UseAnalysisStatusOptions {
  exerciseId: string;
  enabled?: boolean;
}

/**
 * Hook to poll server for analysis status.
 * Polls every 2 seconds when analysis is RUNNING, otherwise every 10 seconds.
 */
export function useAnalysisStatus({ exerciseId, enabled = true }: UseAnalysisStatusOptions) {
  const query = useQuery({
    queryKey: ['analysisStatus', exerciseId],
    queryFn: () => fetchAnalysisStatus(exerciseId),
    enabled: enabled && !!exerciseId,
    // Poll faster when running
    refetchInterval: query => {
      const status = query.state.data;
      if (status?.state === 'RUNNING') {
        return 2000; // 2 seconds when running
      }
      return 10000; // 10 seconds otherwise
    },
    staleTime: 1000, // Consider data stale after 1 second
  });

  return {
    status: query.data ?? {
      state: 'IDLE' as const,
      totalTeams: 0,
      processedTeams: 0,
    },
    isLoading: query.isLoading,
    error: query.error,
    refetch: query.refetch,
  };
}

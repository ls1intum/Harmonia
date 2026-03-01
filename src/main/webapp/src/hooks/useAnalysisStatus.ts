import { useQuery } from '@tanstack/react-query';
import type { AnalysisStatusDTO } from '@/app/generated';
import { analysisApi } from '@/lib/apiClient';

export type AnalysisState = 'IDLE' | 'RUNNING' | 'CANCELLED' | 'DONE' | 'ERROR';
export type AnalysisMode = 'SIMPLE' | 'FULL';
export type AnalysisStatus = Omit<AnalysisStatusDTO, 'state'> & { state: AnalysisState; analysisMode?: AnalysisMode };

/**
 * Fetches analysis status from the server using generated API.
 */
async function fetchAnalysisStatus(exerciseId: string): Promise<AnalysisStatus> {
  const response = await analysisApi.getStatus(parseInt(exerciseId));
  const data = response.data;

  // Validate and cast state to the expected type
  const state = data.state as AnalysisStatus['state'];
  const validStates: AnalysisStatus['state'][] = ['IDLE', 'RUNNING', 'CANCELLED', 'DONE', 'ERROR'];
  const finalState = validStates.includes(state) ? state : 'IDLE';

  const analysisMode = data.analysisMode === 'SIMPLE' ? 'SIMPLE' : data.analysisMode === 'FULL' ? 'FULL' : undefined;

  return {
    state: finalState,
    totalTeams: data.totalTeams || 0,
    processedTeams: data.processedTeams || 0,
    currentTeamName: data.currentTeamName,
    currentStage: data.currentStage,
    errorMessage: data.errorMessage,
    analysisMode,
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
export async function clearData(exerciseId: string, type: 'db' | 'files' | 'both', clearMappings?: boolean): Promise<void> {
  await analysisApi.clearData(parseInt(exerciseId), type, clearMappings);
}

interface UseAnalysisStatusOptions {
  exerciseId: string;
  enabled?: boolean;
}

/**
 * Hook to fetch analysis status from server.
 * Fetches once on mount, then relies on SSE for updates during analysis.
 * Only re-fetches on explicit refetch() calls.
 */
export function useAnalysisStatus({ exerciseId, enabled = true }: UseAnalysisStatusOptions) {
  const query = useQuery({
    queryKey: ['analysisStatus', exerciseId],
    queryFn: () => fetchAnalysisStatus(exerciseId),
    enabled: enabled && !!exerciseId,
    staleTime: 30 * 1000,
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

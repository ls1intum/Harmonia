import { useQuery } from '@tanstack/react-query';
import type { AnalysisStatus } from '@/components/ActivityLog';

/**
 * Fetches analysis status from the server.
 */
async function fetchAnalysisStatus(exerciseId: string): Promise<AnalysisStatus> {
    const response = await fetch(`/api/analysis/${exerciseId}/status`);
    if (!response.ok) {
        throw new Error('Failed to fetch analysis status');
    }
    const data = await response.json();
    return {
        state: data.state || 'IDLE',
        totalTeams: data.totalTeams || 0,
        processedTeams: data.processedTeams || 0,
        currentTeamName: data.currentTeamName,
        currentStage: data.currentStage,
        errorMessage: data.errorMessage,
    };
}

/**
 * Starts a new analysis for the given exercise.
 */
export async function startAnalysis(exerciseId: string): Promise<void> {
    const response = await fetch(`/api/analysis/${exerciseId}/start`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
    });
    if (!response.ok) {
        throw new Error('Failed to start analysis');
    }
}

/**
 * Cancels a running analysis.
 */
export async function cancelAnalysis(exerciseId: string): Promise<void> {
    const response = await fetch(`/api/analysis/${exerciseId}/cancel`, {
        method: 'POST',
    });
    if (!response.ok) {
        throw new Error('Failed to cancel analysis');
    }
}

/**
 * Clears data for an exercise.
 */
export async function clearData(exerciseId: string, type: 'db' | 'files' | 'both'): Promise<void> {
    const response = await fetch(`/api/analysis/${exerciseId}/clear?type=${type}`, {
        method: 'DELETE',
    });
    if (!response.ok) {
        throw new Error('Failed to clear data');
    }
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

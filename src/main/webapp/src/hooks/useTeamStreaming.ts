import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { loadBasicTeamDataStream } from '@/data/dataLoaders';
import type { Team } from '@/types/team';
import { toast } from '@/hooks/use-toast';

interface UseTeamStreamingProps {
  course: string;
  exercise: string;
  enabled?: boolean;
}

interface UseTeamStreamingReturn {
  totalRepos: number;
  processedRepos: number;
  isStreaming: boolean;
  progress: number;
  currentPhase: 'GIT_ANALYSIS' | 'AI_ANALYSIS' | null;
  gitProcessedRepos: number;
}

/**
 * Custom hook for streaming team data via SSE
 * Encapsulates the streaming logic and integrates with React Query cache
 * Supports phased analysis: Git Analysis → AI Analysis
 */
export function useTeamStreaming({ course, exercise, enabled = true }: UseTeamStreamingProps): UseTeamStreamingReturn {
  const queryClient = useQueryClient();
  const [totalRepos, setTotalRepos] = useState(0);
  const [processedRepos, setProcessedRepos] = useState(0);
  const [gitProcessedRepos, setGitProcessedRepos] = useState(0);
  const [isStreaming, setIsStreaming] = useState(false);
  const [currentPhase, setCurrentPhase] = useState<'GIT_ANALYSIS' | 'AI_ANALYSIS' | null>(null);

  useEffect(() => {
    if (!enabled || !course || !exercise) return;

    // Check if we already have cached teams for this exercise
    const cachedTeams = queryClient.getQueryData<Team[]>(['teams', exercise]);
    if (cachedTeams && cachedTeams.length > 0) {
      // We have cached data, don't re-stream
      return;
    }

    // Only stream if we don't have cached data
    const streamedTeams: Team[] = [];

    // Initialize state for streaming
    let mounted = true;

    // Use Promise.resolve to defer setState to avoid the ESLint error
    Promise.resolve().then(() => {
      if (mounted) {
        setTotalRepos(0);
        setProcessedRepos(0);
        setGitProcessedRepos(0);
        setIsStreaming(true);
        setCurrentPhase(null);
      }
    });

    const closeStream = loadBasicTeamDataStream(
      exercise,
      total => {
        if (mounted) {
          setTotalRepos(total);
        }
      },
      // onInit: Add team with pending status (no CQI yet)
      team => {
        streamedTeams.push(team);
        if (mounted) {
          // Update React Query cache with pending teams
          queryClient.setQueryData(['teams', exercise], [...streamedTeams]);
        }
      },
      // onUpdate: Replace or merge with analyzed team data
      teamUpdate => {
        // Handle both full Team and Partial<Team> updates
        const teamId = teamUpdate.id;
        if (!teamId) return; // Skip if no id (shouldn't happen)

        const index = streamedTeams.findIndex(t => t.id === teamId);
        if (index !== -1) {
          // Merge: keep existing data, override with new data
          streamedTeams[index] = { ...streamedTeams[index], ...teamUpdate };
        } else if ('teamName' in teamUpdate && 'students' in teamUpdate) {
          // Only add if it's a full team object
          streamedTeams.push(teamUpdate as Team);
        }
        if (mounted) {
          // Only increment processedRepos for GIT_UPDATE (git done) and AI_UPDATE (fully done)
          // GIT_ANALYZING and AI_ANALYZING are status updates, not completions
          const status = teamUpdate.analysisStatus;
          if (status === 'GIT_DONE') {
            setGitProcessedRepos(prev => prev + 1);
          } else if (status === 'DONE') {
            setProcessedRepos(prev => prev + 1);
          }
          // Update React Query cache with analyzed data
          queryClient.setQueryData(['teams', exercise], [...streamedTeams]);
        }
      },
      () => {
        if (mounted) {
          setIsStreaming(false);
          setCurrentPhase(null);
        }
      },
      (error: unknown) => {
        if (mounted) {
          setIsStreaming(false);
          setCurrentPhase(null);
          toast({
            variant: 'destructive',
            title: 'Error loading teams',
            description: error instanceof Error ? error.message : 'Connection lost or failed.',
          });
        }
      },
      // onPhaseChange: Update current phase
      (phase, _total) => {
        if (mounted) {
          setCurrentPhase(phase);
          // Reset processed count when switching to AI phase
          if (phase === 'AI_ANALYSIS') {
            setProcessedRepos(0);
          }
        }
      },
      // onGitDone: All git analysis complete
      processed => {
        if (mounted) {
          setGitProcessedRepos(processed);
        }
      },
    );

    return () => {
      mounted = false;
      closeStream();
    };
  }, [exercise, course, queryClient, enabled]);

  // Calculate progress based on current phase
  const progress = totalRepos > 0
    ? (currentPhase === 'AI_ANALYSIS'
        ? Math.round((processedRepos / totalRepos) * 100)
        : Math.round((gitProcessedRepos / totalRepos) * 100))
    : 0;

  return {
    totalRepos,
    processedRepos,
    isStreaming,
    progress,
    currentPhase,
    gitProcessedRepos,
  };
}

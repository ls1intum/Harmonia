import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { loadBasicTeamDataStream, type ComplexTeamData } from '@/data/dataLoaders';
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
}

/**
 * Custom hook for streaming team data via SSE
 * Encapsulates the streaming logic and integrates with React Query cache
 */
export function useTeamStreaming({ course, exercise, enabled = true }: UseTeamStreamingProps): UseTeamStreamingReturn {
  const queryClient = useQueryClient();
  const [totalRepos, setTotalRepos] = useState(0);
  const [processedRepos, setProcessedRepos] = useState(0);
  const [isStreaming, setIsStreaming] = useState(false);

  useEffect(() => {
    if (!enabled || !course || !exercise) return;

    // Check if we already have cached teams for this exercise
    const cachedTeams = queryClient.getQueryData<ComplexTeamData[]>(['teams', exercise]);
    if (cachedTeams && cachedTeams.length > 0) {
      // We have cached data, don't re-stream
      return;
    }

    // Only stream if we don't have cached data
    const streamedTeams: ComplexTeamData[] = [];

    // Initialize state for streaming
    let mounted = true;

    // Use Promise.resolve to defer setState to avoid the ESLint error
    Promise.resolve().then(() => {
      if (mounted) {
        setTotalRepos(0);
        setProcessedRepos(0);
        setIsStreaming(true);
      }
    });

    const closeStream = loadBasicTeamDataStream(
      exercise,
      total => {
        if (mounted) {
          setTotalRepos(total);
        }
      },
      team => {
        streamedTeams.push(team);
        if (mounted) {
          setProcessedRepos(streamedTeams.length);
          // Update React Query cache in real-time
          queryClient.setQueryData(['teams', exercise], streamedTeams);
        }
      },
      () => {
        if (mounted) {
          setIsStreaming(false);
        }
      },
      error => {
        if (mounted) {
          setIsStreaming(false);
          toast({
            variant: 'destructive',
            title: 'Error loading teams',
            description: error instanceof Error ? error.message : 'Connection lost or failed.',
          });
        }
      },
    );

    return () => {
      mounted = false;
      closeStream();
    };
  }, [exercise, course, queryClient, enabled]);

  // Calculate progress
  const progress = totalRepos > 0 ? Math.round((processedRepos / totalRepos) * 100) : 0;

  return {
    totalRepos,
    processedRepos,
    isStreaming,
    progress,
  };
}

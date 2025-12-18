import { useState, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import TeamsList from '@/components/TeamsList';
import type { Team } from '@/types/team';
import { loadBasicTeamDataStream, triggerReanalysis, type ComplexTeamData } from '@/data/dataLoaders';
import { toast } from '@/hooks/use-toast';

export default function Teams() {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { course, exercise } = location.state || {};

  const [totalRepos, setTotalRepos] = useState(0);
  const [processedRepos, setProcessedRepos] = useState(0);
  const [isStreaming, setIsStreaming] = useState(false);

  // Use React Query to cache teams data per exercise
  const { data: teams = [] } = useQuery({
    queryKey: ['teams', exercise],
    queryFn: async () => {
      // This function won't be called if data is already cached
      // We'll manually set the data via setQueryData instead
      return [];
    },
    staleTime: Infinity, // Never auto-refetch - only manual invalidation
    gcTime: 10 * 60 * 1000, // Keep in cache for 10 minutes
    enabled: !!exercise,
  });

  useEffect(() => {
    if (!course || !exercise) return;

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
        console.error('Stream error:', error);
        if (mounted) {
          setIsStreaming(false);
          toast({
            variant: 'destructive',
            title: 'Error loading teams',
            description: 'Connection lost or failed.',
          });
        }
      },
    );

    return () => {
      mounted = false;
      closeStream();
    };
  }, [exercise, course, queryClient]); // Only depend on exercise and course

  // Calculate progress
  const progress = totalRepos > 0 ? Math.round((processedRepos / totalRepos) * 100) : 0;

  // Mutation for recompute
  const reanalyzeMutation = useMutation({
    mutationFn: async () => {
      toast({ title: 'Triggering reanalysis...' });
      await triggerReanalysis(course, exercise);
    },
    onSuccess: () => {
      // Invalidate cached teams data to force re-fetch
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      window.location.reload();
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'Failed to trigger reanalysis',
      });
    },
  });

  // Redirect if no course/exercise
  if (!course || !exercise) {
    navigate('/');
    return null;
  }

  const handleTeamSelect = (team: Team) => {
    navigate(`/teams/${team.id}`, { state: { team, course, exercise } });
  };

  const handleRecompute = () => {
    reanalyzeMutation.mutate();
  };

  return (
    <TeamsList
      teams={teams}
      onTeamSelect={handleTeamSelect}
      onBackToHome={() => navigate('/')}
      onRecompute={handleRecompute}
      course={course}
      exercise={exercise}
      isAnalyzing={isStreaming || reanalyzeMutation.isPending}
      progress={progress}
    />
  );
}

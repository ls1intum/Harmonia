import { useLocation, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import TeamsList from '@/components/TeamsList';
import type { Team } from '@/types/team';
import { triggerReanalysis } from '@/data/dataLoaders';
import { toast } from '@/hooks/use-toast';
import { useTeamStreaming } from '@/hooks/useTeamStreaming';

export default function Teams() {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { course, exercise } = location.state || {};

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

  // Use custom hook for SSE streaming (encapsulates useEffect logic)
  const { isStreaming, progress, logs } = useTeamStreaming({
    course,
    exercise,
    enabled: !!course && !!exercise,
  });

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
      logs={logs}
    />
  );
}

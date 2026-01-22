import { useLocation, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import TeamsList from '@/components/TeamsList';
import type { Team } from '@/types/team';
import { toast } from '@/hooks/use-toast';
import { useAnalysisStatus, cancelAnalysis, clearData } from '@/hooks/useAnalysisStatus';
import { loadBasicTeamDataStream, transformToComplexTeamData } from '@/data/dataLoaders';
import type { ClientResponseDTO } from '@/app/generated';

export default function Teams() {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { course, exercise } = location.state || {};

  // Use new server-synced status hook
  const { status, refetch: refetchStatus } = useAnalysisStatus({
    exerciseId: exercise,
    enabled: !!exercise,
  });

  // Fetch teams from database on load
  const { data: teams = [] } = useQuery({
    queryKey: ['teams', exercise],
    queryFn: async () => {
      // Fetch already-analyzed teams from database (doesn't trigger new analysis)
      const response = await fetch('/api/requestResource/getData');
      if (!response.ok) return [];
      const data: ClientResponseDTO[] = await response.json();
      // Use the proper transformation function that includes subMetrics (balanceScore, pairingScore)
      // This ensures Contribution Balance and Pair Programming Signals are always available
      return data.map(transformToComplexTeamData);
    },
    staleTime: 30 * 1000, // 30 seconds
    gcTime: 10 * 60 * 1000,
    enabled: !!exercise,
  });

  // Mutation for starting analysis
  const startMutation = useMutation({
    mutationFn: async () => {
      toast({ title: 'Starting analysis...' });
      // Start streaming
      return new Promise<void>((resolve, reject) => {
        loadBasicTeamDataStream(
          exercise,
          () => {}, // onTotal
          team => {
            // Update cache with new team
            queryClient.setQueryData(['teams', exercise], (old: Team[] = []) => [...old, team as unknown as Team]);
          },
          () => {
            refetchStatus();
            resolve();
          },
          error => {
            reject(error);
          },
        );
      });
    },
    onSuccess: () => {
      toast({ title: 'Analysis completed!' });
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      refetchStatus();
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'Failed to start analysis',
      });
      refetchStatus();
    },
  });

  // Mutation for cancelling
  const cancelMutation = useMutation({
    mutationFn: () => cancelAnalysis(exercise),
    onSuccess: () => {
      toast({ title: 'Analysis cancelled' });
      refetchStatus();
    },
  });

  // Mutation for recompute (force)
  const recomputeMutation = useMutation({
    mutationFn: async () => {
      toast({ title: 'Forcing reanalysis...' });
      // Clear DB first, then start fresh
      await clearData(exercise, 'db');
      // Trigger start
      startMutation.mutate();
    },
  });

  // Mutation for clear
  const clearMutation = useMutation({
    mutationFn: (type: 'db' | 'files' | 'both') => clearData(exercise, type),
    onSuccess: () => {
      toast({ title: 'Data cleared successfully' });
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      refetchStatus();
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'Failed to clear data',
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

  return (
    <TeamsList
      teams={teams}
      onTeamSelect={handleTeamSelect}
      onBackToHome={() => navigate('/')}
      onStart={() => startMutation.mutate()}
      onCancel={() => cancelMutation.mutate()}
      onRecompute={() => recomputeMutation.mutate()}
      onClear={type => clearMutation.mutate(type)}
      course={course}
      exercise={exercise}
      analysisStatus={status}
    />
  );
}

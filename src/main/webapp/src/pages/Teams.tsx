import { useLocation, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import TeamsList from '@/components/TeamsList';
import type { Team } from '@/types/team';
import { toast } from '@/hooks/use-toast';
import { useAnalysisStatus, cancelAnalysis, clearData } from '@/hooks/useAnalysisStatus';
import { loadBasicTeamDataStream, transformToComplexTeamData } from '@/data/dataLoaders';

export default function Teams() {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { course, exercise } = location.state || {};

  // Use new server-synced status hook
  const {
    status,
    refetch: refetchStatus,
    isLoading: isStatusLoading,
  } = useAnalysisStatus({
    exerciseId: exercise,
    enabled: !!exercise,
  });

  // Fetch teams from database on load - disabled during analysis
  const isAnalysisRunning = status.state === 'RUNNING';
  const { data: teams = [] } = useQuery<Team[]>({
    queryKey: ['teams', exercise],
    queryFn: async () => {
      // Fetch already-analyzed teams from database (filtered by exerciseId)
      const response = await fetch(`/api/requestResource/${exercise}/getData`);
      if (!response.ok) return [];
      const data = await response.json();
      // Transform to Team type
      return data.map(transformToComplexTeamData);
    },
    staleTime: 30 * 1000, // 30 seconds
    gcTime: 10 * 60 * 1000,
    enabled: !!exercise && !isAnalysisRunning, // Disable during analysis
    refetchOnWindowFocus: !isAnalysisRunning, // Don't refetch when analysis is running
  });

  // Mutation for starting analysis
  const startMutation = useMutation({
    mutationFn: async () => {
      // Step 1: Immediately update UI - clear teams cache and set status to RUNNING
      // This ensures the button changes immediately to "Cancel"
      queryClient.setQueryData(['teams', exercise], []);
      queryClient.setQueryData(['analysisStatus', exercise], {
        state: 'RUNNING' as const,
        totalTeams: 0,
        processedTeams: 0,
        currentTeamName: undefined,
        currentStage: undefined,
      });

      toast({ title: 'Starting analysis...' });

      // Step 2: Start streaming (backend will clear data before starting)
      return new Promise<void>((resolve, reject) => {
        loadBasicTeamDataStream(
          exercise,
          () => {}, // onTotal
          // onInit: Add team with pending status
          team => {
            queryClient.setQueryData(['teams', exercise], (old: Team[] = []) => {
              const exists = old.some(t => t.id === (team as unknown as Team).id);
              if (exists) return old;
              return [...old, team as unknown as Team];
            });
          },
          // onUpdate: Replace existing team with analyzed data
          team => {
            queryClient.setQueryData(['teams', exercise], (old: Team[] = []) => {
              const teamData = team as unknown as Team;
              const exists = old.some(t => t.id === teamData.id);
              if (exists) {
                return old.map(t => (t.id === teamData.id ? teamData : t));
              }
              return [...old, teamData];
            });
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
    mutationFn: async () => {
      // Optimistically update status to CANCELLED immediately
      queryClient.setQueryData(['analysisStatus', exercise], (old: typeof status) => ({
        ...old,
        state: 'CANCELLED' as const,
      }));
      toast({ title: 'Cancelling analysis...' });
      return cancelAnalysis(exercise);
    },
    onSuccess: () => {
      toast({ title: 'Analysis cancelled' });
      // Invalidate to get fresh data including cancelled teams
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      refetchStatus();
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'Failed to cancel analysis' });
      refetchStatus();
    },
  });

  // Mutation for recompute (force) - same as start since backend clears data first
  const recomputeMutation = useMutation({
    mutationFn: async () => {
      // Step 1: Immediately update UI - clear teams cache and set status to RUNNING
      queryClient.setQueryData(['teams', exercise], []);
      queryClient.setQueryData(['analysisStatus', exercise], {
        state: 'RUNNING' as const,
        totalTeams: 0,
        processedTeams: 0,
        currentTeamName: undefined,
        currentStage: undefined,
      });

      toast({ title: 'Forcing reanalysis...' });

      // Step 2: Start streaming (backend will clear data before starting)
      return new Promise<void>((resolve, reject) => {
        loadBasicTeamDataStream(
          exercise,
          () => {},
          team => {
            queryClient.setQueryData(['teams', exercise], (old: Team[] = []) => {
              const exists = old.some(t => t.id === (team as unknown as Team).id);
              if (exists) return old;
              return [...old, team as unknown as Team];
            });
          },
          team => {
            queryClient.setQueryData(['teams', exercise], (old: Team[] = []) => {
              const teamData = team as unknown as Team;
              const exists = old.some(t => t.id === teamData.id);
              if (exists) {
                return old.map(t => (t.id === teamData.id ? teamData : t));
              }
              return [...old, teamData];
            });
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
      toast({ title: 'Reanalysis completed!' });
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      refetchStatus();
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'Failed to reanalyze',
      });
      refetchStatus();
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
      isLoading={isStatusLoading}
    />
  );
}

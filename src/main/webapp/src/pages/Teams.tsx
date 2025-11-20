import { useLocation, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import TeamsList from '@/components/TeamsList';
import type { Team } from '@/types/team';
import { loadBasicTeamData, loadComplexTeamData, triggerReanalysis } from '@/data/dataLoaders';
import { toast } from '@/hooks/use-toast';

export default function Teams() {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { course, exercise } = location.state || {};

  // Query 1: Fetch basic data (fast)
  const { data: basicTeams, isLoading: isLoadingBasic } = useQuery({
    queryKey: ['teams', 'basic', course, exercise],
    queryFn: async () => {
      return await loadBasicTeamData(course, exercise);
    },
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

  // Query 2: Fetch complex data (slow, LLM-based)
  const { data: complexTeams, isLoading: isLoadingComplex } = useQuery({
    queryKey: ['teams', 'complex', course, exercise],
    queryFn: async () => {
      return await loadComplexTeamData(course, exercise);
    },
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

  // Merge data: use complex if available, otherwise basic
  const teams = complexTeams || basicTeams || [];

  // Calculate progress
  const basicLoaded = !!basicTeams;
  const complexLoaded = !!complexTeams;
  const isAnalyzing = isLoadingBasic || isLoadingComplex;

  const progress = (() => {
    if (complexLoaded) return 100;
    if (basicLoaded) return 50;
    if (isLoadingBasic) return 25;
    return 0;
  })();

  // Mutation for recompute
  const reanalyzeMutation = useMutation({
    mutationFn: async () => {
      toast({ title: 'Triggering reanalysis...' });
      await triggerReanalysis(course, exercise);
    },
    onSuccess: () => {
      // Invalidate both queries to refetch
      queryClient.invalidateQueries({ queryKey: ['teams', 'basic', course, exercise] });
      queryClient.invalidateQueries({ queryKey: ['teams', 'complex', course, exercise] });
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
      isAnalyzing={isAnalyzing || reanalyzeMutation.isPending}
      progress={progress}
    />
  );
}

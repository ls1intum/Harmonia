import { useState, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import TeamsList from '@/components/TeamsList';
import type { Team } from '@/types/team';
import { loadBasicTeamDataStream, triggerReanalysis, type BasicTeamData } from '@/data/dataLoaders';
import { toast } from '@/hooks/use-toast';

export default function Teams() {
  const location = useLocation();
  const navigate = useNavigate();
  const { course, exercise } = location.state || {};

  const [teams, setTeams] = useState<BasicTeamData[]>([]);
  const [totalRepos, setTotalRepos] = useState(0);
  const [processedRepos, setProcessedRepos] = useState(0);
  const [isStreaming, setIsStreaming] = useState(false);

  useEffect(() => {
    if (!course || !exercise) return;

    // Reset state
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setTeams([]);
    setTotalRepos(0);
    setProcessedRepos(0);
    setIsStreaming(true);

    const closeStream = loadBasicTeamDataStream(
      exercise,
      (total) => setTotalRepos(total),
      (team) => {
        setTeams((prev) => {
          // Avoid duplicates if any
          if (prev.some(t => t.id === team.id)) return prev;
          return [...prev, team];
        });
        setProcessedRepos((prev) => prev + 1);
      },
      () => setIsStreaming(false),
      (error) => {
        console.error('Stream error:', error);
        setIsStreaming(false);
        toast({
          variant: 'destructive',
          title: 'Error loading teams',
          description: 'Connection lost or failed.',
        });
      }
    );

    return () => closeStream();
  }, [course, exercise]);

  // Calculate progress
  const progress = totalRepos > 0 ? Math.round((processedRepos / totalRepos) * 100) : 0;

  // Mutation for recompute
  const reanalyzeMutation = useMutation({
    mutationFn: async () => {
      toast({ title: 'Triggering reanalysis...' });
      await triggerReanalysis(course, exercise);
    },
    onSuccess: () => {
      // Reload page or re-trigger stream
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

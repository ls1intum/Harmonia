import { useState, useEffect, useEffectEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import TeamsList from '@/components/TeamsList';
import type { Team } from '@/types/team';
import { loadTeamDataProgressive, triggerReanalysis, type BasicTeamData, type ComplexTeamData } from '@/data/dataLoaders';
import { toast } from '@/hooks/use-toast';

export default function Teams() {
  const location = useLocation();
  const navigate = useNavigate();
  const { course, exercise } = location.state || {};

  const [teams, setTeams] = useState<Team[]>([]);
  const [isAnalyzing, setIsAnalyzing] = useState(true);
  const [progress, setProgress] = useState(0);
  const [basicLoaded, setBasicLoaded] = useState(false);
  const [complexLoaded, setComplexLoaded] = useState(false);

  const startAnalysis = async () => {
    setIsAnalyzing(true);
    setProgress(0);
    setBasicLoaded(false);
    setComplexLoaded(false);

    // Simulate progress animation
    const progressInterval = setInterval(() => {
      setProgress(prev => {
        if (basicLoaded && prev < 50) return 50;
        if (complexLoaded && prev < 100) return 100;
        if (prev >= 100) {
          clearInterval(progressInterval);
          setIsAnalyzing(false);
          return 100;
        }
        return Math.min(prev + 5, basicLoaded ? 50 : 45);
      });
    }, 100);

    try {
      // Load data progressively
      await loadTeamDataProgressive(
        course,
        exercise,
        // On basic data loaded (fast, partial data)
        (basicTeams: BasicTeamData[]) => {
          setTeams(basicTeams as Team[]);
          setBasicLoaded(true);
          setProgress(50);
        },
        // On complex data loaded (slow, complete data)
        (complexTeams: ComplexTeamData[]) => {
          setTeams(complexTeams);
          setComplexLoaded(true);
          setProgress(100);
          setIsAnalyzing(false);
          clearInterval(progressInterval);
        },
        // On error
        (error: Error) => {
          clearInterval(progressInterval);
          setIsAnalyzing(false);
          toast.error('Failed to load team data', error.message);
        },
      );
    } catch {
      clearInterval(progressInterval);
      setIsAnalyzing(false);
      toast.error('An unexpected error occurred');
    }

    return () => clearInterval(progressInterval);
  };

  const onStartAnalysis = useEffectEvent(startAnalysis);

  useEffect(() => {
    if (!course || !exercise) {
      navigate('/');
      return;
    }

    onStartAnalysis().catch(console.error);
  }, [course, exercise, navigate]);

  const handleTeamSelect = (team: Team) => {
    navigate(`/teams/${team.id}`, { state: { team, course, exercise } });
  };

  const handleRecompute = async () => {
    try {
      toast({ title: 'Triggering reanalysis...' });
      await triggerReanalysis(course, exercise);
      // After triggering, restart the analysis
      await startAnalysis();
    } catch {
      toast.error('Failed to trigger reanalysis');
    }
  };

  return (
    <TeamsList
      teams={teams}
      onTeamSelect={handleTeamSelect}
      onBackToHome={() => navigate('/')}
      onRecompute={handleRecompute}
      course={course}
      exercise={exercise}
      isAnalyzing={isAnalyzing}
      progress={progress}
    />
  );
}

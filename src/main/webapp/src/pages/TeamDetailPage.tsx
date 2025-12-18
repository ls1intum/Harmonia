import { useParams, useNavigate, useLocation } from 'react-router-dom';
import TeamDetail from '@/components/TeamDetail';
import { loadTeamById } from '@/data/dataLoaders';

import { useQuery } from '@tanstack/react-query';

export default function TeamDetailPage() {
  const { teamId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { course, exercise } = location.state || {};

  const {
    data: team,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['team', teamId],
    queryFn: async () => {
      if (!teamId) {
        throw new Error('No team ID provided');
      }
      const fetchedTeam = await loadTeamById(teamId, exercise);
      if (!fetchedTeam) {
        throw new Error('Team not found');
      }
      return fetchedTeam;
    },
  });

  if (isLoading) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto mb-4"></div>
          <p className="text-muted-foreground">Loading team details...</p>
        </div>
      </div>
    );
  }

  if (error || !team) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="text-center">
          <p className="text-destructive mb-4">{error?.message || 'Team not found'}</p>
          <button onClick={() => navigate('/teams')} className="text-primary hover:underline">
            Back to Teams
          </button>
        </div>
      </div>
    );
  }

  return <TeamDetail team={team} onBack={() => navigate('/teams', { state: { course, exercise } })} course={course} exercise={exercise} />;
}

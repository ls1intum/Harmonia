import { useParams, useNavigate, useLocation } from 'react-router-dom';
import TeamDetail from '@/components/TeamDetail';
import type { ComplexTeamData } from '@/data/dataLoaders';

export default function TeamDetailPage() {
  const { teamId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { team, course, exercise } = location.state || {};

  if (!team) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="text-center">
          <p className="text-destructive mb-4">Team not found</p>
          <button onClick={() => navigate('/teams')} className="text-primary hover:underline">
            Back to Teams
          </button>
        </div>
      </div>
    );
  }

  return <TeamDetail team={team} onBack={() => navigate('/teams', { state: { course, exercise } })} course={course} exercise={exercise} />;
}

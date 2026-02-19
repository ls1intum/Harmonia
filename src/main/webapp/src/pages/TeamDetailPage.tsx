import { useNavigate, useLocation } from 'react-router-dom';
import TeamDetail from '@/components/TeamDetail';
import type { PairProgrammingBadgeStatus } from '@/lib/pairProgramming';
import type { Team } from '@/types/team';

export default function TeamDetailPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { team, course, exercise, pairProgrammingEnabled, pairProgrammingBadgeStatus } = (location.state || {}) as {
    team?: Team;
    course?: string;
    exercise?: string;
    pairProgrammingEnabled?: boolean;
    pairProgrammingBadgeStatus?: PairProgrammingBadgeStatus | null;
  };

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

  return (
    <TeamDetail
      team={team}
      onBack={() => navigate('/teams', { state: { course, exercise, pairProgrammingEnabled } })}
      course={course}
      exercise={exercise}
      pairProgrammingBadgeStatus={pairProgrammingBadgeStatus}
    />
  );
}

import { useNavigate, useLocation } from 'react-router-dom';
import { useState } from 'react';
import TeamDetail from '@/components/TeamDetail';
import type { PairProgrammingBadgeStatus } from '@/lib/pairProgramming';
import type { TeamDTO } from '@/data/dataLoaders';
import type { CourseAverages } from '@/lib/courseAverages';

/** Route page for a single team. Reads the team from router location state and delegates to TeamDetail. */
export default function TeamDetailPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const {
    team: initialTeam,
    course,
    exercise,
    pairProgrammingEnabled,
    pairProgrammingBadgeStatus,
    courseAverages,
  } = (location.state || {}) as {
    team?: TeamDTO;
    course?: string;
    exercise?: string;
    pairProgrammingEnabled?: boolean;
    pairProgrammingBadgeStatus?: PairProgrammingBadgeStatus | null;
    courseAverages?: CourseAverages | null;
  };

  const [team, setTeam] = useState<TeamDTO | undefined>(initialTeam);

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
      courseAverages={courseAverages}
      onTeamUpdate={setTeam}
    />
  );
}

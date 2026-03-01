import { useNavigate, useLocation, useParams } from 'react-router-dom';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import TeamDetail from '@/components/TeamDetail';
import type { PairProgrammingBadgeStatus } from '@/lib/pairProgramming';
import { transformToComplexTeamData, type TeamDTO } from '@/data/dataLoaders';
import type { CourseAverages } from '@/lib/courseAverages';
import { requestApi } from '@/lib/apiClient';
import { Loader2 } from 'lucide-react';

/** Route page for a single team. Fetches full team detail on demand via API. */
export default function TeamDetailPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { teamId: teamIdParam } = useParams<{ teamId: string }>();
  const {
    teamId: stateTeamId,
    course,
    exercise,
    pairProgrammingEnabled,
    pairProgrammingBadgeStatus,
    courseAverages,
    analysisMode,
  } = (location.state || {}) as {
    teamId?: number;
    course?: string;
    exercise?: string;
    pairProgrammingEnabled?: boolean;
    pairProgrammingBadgeStatus?: PairProgrammingBadgeStatus | null;
    courseAverages?: CourseAverages | null;
    analysisMode?: 'SIMPLE' | 'FULL';
  };

  const resolvedTeamId = stateTeamId ?? (teamIdParam ? parseInt(teamIdParam) : undefined);

  // Lazy fetch full team detail from server
  const {
    data: fetchedTeam,
    isLoading,
    error,
  } = useQuery<TeamDTO | null>({
    queryKey: ['teamDetail', exercise, resolvedTeamId],
    queryFn: async () => {
      if (!exercise || !resolvedTeamId) return null;
      const response = await requestApi.getTeamDetail(parseInt(exercise), resolvedTeamId);
      return transformToComplexTeamData(response.data);
    },
    enabled: !!exercise && !!resolvedTeamId,
    staleTime: 30 * 1000,
  });

  const [team, setTeam] = useState<TeamDTO | undefined>(undefined);
  const displayTeam = team ?? fetchedTeam ?? undefined;

  if (isLoading) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="flex items-center gap-3 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span>Loading team details...</span>
        </div>
      </div>
    );
  }

  if (error || !displayTeam) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="text-center">
          <p className="text-destructive mb-4">{error ? 'Failed to load team' : 'Team not found'}</p>
          <button
            onClick={() => navigate('/teams', { state: { course, exercise, pairProgrammingEnabled } })}
            className="text-primary hover:underline"
          >
            Back to Teams
          </button>
        </div>
      </div>
    );
  }

  return (
    <TeamDetail
      team={displayTeam}
      onBack={() => navigate('/teams', { state: { course, exercise, pairProgrammingEnabled } })}
      course={course}
      exercise={exercise}
      pairProgrammingBadgeStatus={pairProgrammingBadgeStatus}
      courseAverages={courseAverages}
      onTeamUpdate={setTeam}
      analysisMode={analysisMode}
    />
  );
}

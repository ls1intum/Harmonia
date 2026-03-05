import { useNavigate, useLocation, useParams } from 'react-router-dom';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import TeamDetail from '@/components/TeamDetail';
import type { PairProgrammingBadgeStatus } from '@/lib/pairProgramming';
import { transformToComplexTeamData, type TeamDTO } from '@/data/dataLoaders';
import type { CourseAverages } from '@/lib/courseAverages';
import { requestApi } from '@/lib/apiClient';
import { toast } from '@/hooks/use-toast';
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
    teamsSearchParams,
  } = (location.state || {}) as {
    teamId?: number;
    course?: string;
    exercise?: string;
    pairProgrammingEnabled?: boolean;
    pairProgrammingBadgeStatus?: PairProgrammingBadgeStatus | null;
    courseAverages?: CourseAverages | null;
    analysisMode?: 'SIMPLE' | 'FULL';
    teamsSearchParams?: string;
  };

  const queryClient = useQueryClient();
  const resolvedTeamId = stateTeamId ?? (teamIdParam ? parseInt(teamIdParam) : undefined);

  const navigateBackToTeams = () => {
    const search = teamsSearchParams ? `?${teamsSearchParams}` : '';
    navigate(`/teams${search}`, { state: { course, exercise, pairProgrammingEnabled } });
  };

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

  const toggleReviewedMutation = useMutation({
    mutationFn: async () => {
      const response = await requestApi.toggleReviewStatus(parseInt(exercise!), resolvedTeamId!);
      return response.data;
    },
    onMutate: async () => {
      // Optimistic update on the local display team
      setTeam(prev => {
        const current = prev ?? fetchedTeam ?? undefined;
        if (!current) return prev;
        return Object.assign({}, current, { isReviewed: !current.isReviewed });
      });
      // Also update the teams list cache so the list stays in sync
      await queryClient.cancelQueries({ queryKey: ['teams', exercise] });
      queryClient.setQueryData(['teams', exercise], (old: TeamDTO[] = []) =>
        old.map(t => (String(t.teamId) === String(resolvedTeamId) ? Object.assign({}, t, { isReviewed: !t.isReviewed }) : t)),
      );
    },
    onError: () => {
      // Revert optimistic update
      setTeam(prev => {
        const current = prev ?? fetchedTeam ?? undefined;
        if (!current) return prev;
        return Object.assign({}, current, { isReviewed: !current.isReviewed });
      });
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      toast({ variant: 'destructive', title: 'Failed to toggle review status' });
    },
  });

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
          <button onClick={() => navigateBackToTeams()} className="text-primary hover:underline">
            Back to Teams
          </button>
        </div>
      </div>
    );
  }

  return (
    <TeamDetail
      team={displayTeam}
      onBack={() => navigateBackToTeams()}
      course={course}
      exercise={exercise}
      pairProgrammingBadgeStatus={pairProgrammingBadgeStatus}
      courseAverages={courseAverages}
      onTeamUpdate={setTeam}
      onToggleReviewed={() => toggleReviewedMutation.mutate()}
      analysisMode={analysisMode}
    />
  );
}

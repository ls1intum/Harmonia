import type { AnalyzedChunkDTO, EmailMappingDTO } from '@/app/generated';
import type { TeamDTO, SubMetric } from '@/data/dataLoaders';
import { transformToComplexTeamData } from '@/data/dataLoaders';
import type { CourseAverages } from '@/lib/courseAverages';
import { computeBasicMetrics } from '@/lib/utils';
import { emailMappingApi, requestApi, analysisApi } from '@/lib/apiClient';
import { useMemo, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ArrowLeft, AlertTriangle, Users, ClipboardCheck, Filter, ExternalLink, Sparkles, Loader2, Ban, CircleCheck } from 'lucide-react';
import MetricCard from './MetricCard';
import AnalysisFeed from './AnalysisFeed';
import ErrorListPanel from './ErrorListPanel';
import OrphanCommitsPanel from './OrphanCommitsPanel';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { readDevModeFromStorage } from '@/lib/devMode';
import { getFailedReason } from '@/lib/utils.ts';
import PairProgrammingBadge from '@/components/PairProgrammingBadge';
import type { PairProgrammingBadgeStatus } from '@/lib/pairProgramming';

interface TeamDetailProps {
  team: TeamDTO;
  onBack: () => void;
  course?: string;
  exercise?: string;
  courseAverages?: CourseAverages | null;
  onTeamUpdate?: (team: TeamDTO) => void;
  onToggleReviewed?: () => void;
  analysisMode?: 'SIMPLE' | 'FULL';
}

/**
 * Full detail view for a single team — CQI score, student list, metrics cards,
 * orphan-commit mapping, and the AI analysis feed.
 *
 * @param team - team to display
 * @param onBack - navigate back to the teams list
 * @param course - course
 * @param exercise - exercise
 * @param courseAverages - course average
 * @param onTeamUpdate
 * @param onToggleReviewed
 * @param analysisMode
 */
const TeamDetail = ({
  team,
  onBack,
  course,
  exercise,
  courseAverages = null,
  onTeamUpdate,
  onToggleReviewed,
  analysisMode,
}: TeamDetailProps) => {
  const isDevMode = readDevModeFromStorage();
  const queryClient = useQueryClient();

  const artemisRepoUrl = useMemo(() => {
    if (!course || !exercise || !team.participationId) return null;
    let baseUrl: string | null = null;
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        baseUrl = window.localStorage.getItem('harmonia.serverUrl');
      }
    } catch {
      baseUrl = null;
    }
    if (!baseUrl) return null;
    baseUrl = baseUrl.replace(/\/+$/, '');
    return `${baseUrl}/course-management/${course}/programming-exercises/${exercise}/repository/USER/${team.participationId}`;
  }, [course, exercise, team.participationId]);

  const { data: emailMappings = [] } = useQuery<EmailMappingDTO[]>({
    queryKey: ['emailMappings', exercise],
    queryFn: async () => {
      const response = await emailMappingApi.getAllMappings(parseInt(exercise!));
      return response.data;
    },
    enabled: !!exercise,
  });

  const dismissedEmails = useMemo(() => {
    const emails = new Set<string>();
    for (const m of emailMappings) {
      if (m.isDismissed && m.gitEmail) {
        emails.add(m.gitEmail.toLowerCase());
      }
    }
    return emails;
  }, [emailMappings]);

  const assignedEmails = useMemo(() => {
    const map = new Map<string, { mappingId: string; studentName: string }>();
    for (const m of emailMappings) {
      if (!m.isDismissed && m.gitEmail && m.studentName && m.id) {
        map.set(m.gitEmail.toLowerCase(), { mappingId: m.id, studentName: m.studentName });
      }
    }
    return map;
  }, [emailMappings]);

  const { data: templateAuthorEmails = [] } = useQuery<string[]>({
    queryKey: ['templateAuthorEmails', exercise],
    queryFn: async () => {
      const response = await emailMappingApi.getTemplateAuthors(parseInt(exercise!));
      const data = response.data;
      if (!Array.isArray(data)) return [];
      return data.map(d => (d.templateEmail ?? '').toLowerCase()).filter(Boolean);
    },
    enabled: !!exercise,
  });

  const mappingChangeMutation = useMutation({
    mutationFn: async () => {
      // Refresh team data from server after a mapping change
      if (onTeamUpdate && exercise) {
        const response = await requestApi.getData(parseInt(exercise));
        const updatedTeam = response.data.find(d => d.teamId === team.teamId);
        if (updatedTeam) {
          const transformed = transformToComplexTeamData(updatedTeam);
          onTeamUpdate(transformed);
          // Sync updated fields into the teams list cache
          queryClient.setQueryData(['teams', exercise], (old: TeamDTO[] = []) =>
            old.map(t =>
              t.teamId === team.teamId
                ? Object.assign({}, t, {
                    orphanCommitCount: updatedTeam.orphanCommitCount,
                    isSuspicious: updatedTeam.isSuspicious,
                    cqi: updatedTeam.cqi,
                    subMetrics: transformed.subMetrics,
                  })
                : t,
            ),
          );
        }
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['emailMappings', exercise] });
    },
  });

  const handleMappingChange = useCallback(() => {
    mappingChangeMutation.mutate();
  }, [mappingChangeMutation]);

  const handleUndoDismiss = useCallback(
    async (email: string) => {
      const mapping = emailMappings.find(m => m.isDismissed && m.gitEmail?.toLowerCase() === email.toLowerCase());
      if (mapping?.id && exercise) {
        await emailMappingApi.deleteMapping(parseInt(exercise), mapping.id);
        handleMappingChange();
      }
    },
    [emailMappings, exercise, handleMappingChange],
  );

  const handleUndoAssignment = useCallback(
    async (email: string) => {
      const info = assignedEmails.get(email.toLowerCase());
      if (info && exercise) {
        await emailMappingApi.deleteMapping(parseInt(exercise), info.mappingId);
        handleMappingChange();
      }
    },
    [assignedEmails, exercise, handleMappingChange],
  );

  const getCQIColor = (cqi: number) => {
    if (cqi >= 80) return 'text-success';
    if (cqi >= 60) return 'text-warning';
    return 'text-destructive';
  };

  const getCQIBgColor = (cqi: number) => {
    if (cqi >= 80) return 'bg-success/10';
    if (cqi >= 60) return 'bg-warning/10';
    return 'bg-destructive/10';
  };

  // Check if team analysis is complete
  const isAnalysisComplete = team.analysisStatus === 'DONE';

  // Check if git analysis is complete (git metrics available)
  const isGitAnalysisComplete =
    team.analysisStatus === 'GIT_DONE' ||
    team.analysisStatus === 'AI_ANALYZING' ||
    team.analysisStatus === 'DONE' ||
    team.analysisStatus === 'CANCELLED';

  // Team is 'failed' if any student has <10 commits (only check if git analysis is complete)
  const isTeamFailed = (team: TeamDTO) => {
    if (!isGitAnalysisComplete) return false;
    return (team.students || []).some(s => (s.commitCount ?? 0) < 10);
  };

  const computeAiMutation = useMutation({
    mutationFn: async () => {
      if (!exercise) throw new Error('No exercise ID');
      const response = await analysisApi.computeAiForTeam(parseInt(exercise), team.teamId ?? 0);
      return response.data;
    },
    onMutate: () => {
      // Optimistically show AI_ANALYZING state so all sections reflect computing
      if (onTeamUpdate) {
        onTeamUpdate(
          Object.assign({}, team, {
            analysisStatus: 'AI_ANALYZING',
            cqi: undefined,
            isSuspicious: undefined,
            subMetrics: undefined,
            analysisHistory: undefined,
          }),
        );
      }
    },
    onSuccess: data => {
      if (onTeamUpdate && exercise) {
        onTeamUpdate(transformToComplexTeamData(data));
      }
      queryClient.invalidateQueries({ queryKey: ['teamDetail', exercise, team.teamId] });
    },
    onError: () => {
      // Revert to previous state on failure
      if (onTeamUpdate) {
        onTeamUpdate(team);
      }
    },
  });

  // Show "Compute AI" when git is done but no AI result yet (effortBalance is 0/null = SIMPLE mode)
  // Show "Recompute AI" when AI was already run (has effortBalance > 0)
  const hasAiResult = (team.subMetrics ?? []).some(m => m.name === 'Effort Balance' && m.value > 0);
  const isAiComputing = team.analysisStatus === 'AI_ANALYZING' || computeAiMutation.isPending;
  const canComputeAi = isGitAnalysisComplete && !!exercise;

  // Check if student metadata is available (show after git analysis is complete)
  const hasStudentMetadata = (student: { commitCount?: number; linesAdded?: number; linesDeleted?: number; linesChanged?: number }) => {
    return (
      isGitAnalysisComplete &&
      student.commitCount !== undefined &&
      student.linesAdded !== undefined &&
      student.linesDeleted !== undefined &&
      student.linesChanged !== undefined
    );
  };

  // Derive PP badge status from server data
  const pairProgrammingBadgeStatus: PairProgrammingBadgeStatus | null = team.pairProgrammingStatus
    ? (team.pairProgrammingStatus.toLowerCase() as PairProgrammingBadgeStatus)
    : null;

  const isSimpleMode = analysisMode === 'SIMPLE';
  const metricsToShow = useMemo((): SubMetric[] => {
    const effortBalancePlaceholder: SubMetric = isAiComputing
      ? {
          name: 'Effort Balance',
          value: -1,
          weight: 0,
          description: 'Is effort distributed fairly among team members?',
          details: 'AI analysis is in progress. This metric will be available when computation completes.',
        }
      : isSimpleMode
        ? {
            name: 'Effort Balance',
            value: -5,
            weight: 0,
            description: 'Is effort distributed fairly among team members?',
            details: 'This metric requires AI analysis. Use the "Compute AI" button above to calculate it.',
          }
        : {
            name: 'Effort Balance',
            value: -1,
            weight: 0,
            description: 'Is effort distributed fairly among team members?',
            details: 'Will be calculated after analysis completes.',
          };
    const pendingPlaceholderMetrics: SubMetric[] = [
      effortBalancePlaceholder,
      {
        name: 'Lines of Code Balance',
        value: -1,
        weight: 0,
        description: 'Are code contributions balanced?',
        details: 'Will be calculated after analysis completes.',
      },
      {
        name: 'Temporal Spread',
        value: -1,
        weight: 0,
        description: 'Is work spread over time or crammed at deadline?',
        details: 'Higher scores mean work was spread consistently throughout the project period. Based on prefiltered commits.',
      },
      {
        name: 'File Ownership Spread',
        value: -1,
        weight: 0,
        description: 'Are files owned by multiple team members?',
        details: 'Will be calculated after analysis completes.',
      },
    ];
    let fromServer = team.subMetrics ?? [];
    // When no metrics from server yet (PENDING/DOWNLOADING/GIT_ANALYZING), show placeholders
    if (fromServer.length === 0) {
      fromServer = pendingPlaceholderMetrics;
    }
    // In SIMPLE mode, override Effort Balance based on AI computation state
    if (!hasAiResult) {
      fromServer = fromServer.map(m => {
        if (m.name !== 'Effort Balance') return m;
        if (isAiComputing) {
          return Object.assign({}, m, {
            value: -1,
            details: 'AI analysis is in progress. This metric will be available when computation completes.',
          });
        }
        if (isSimpleMode) {
          return Object.assign({}, m, {
            value: -5,
            details: 'This metric requires AI analysis. Use the "Compute AI" button above to calculate it.',
          });
        }
        return m;
      });
    }
    return fromServer;
  }, [team.subMetrics, isSimpleMode, hasAiResult, isAiComputing]);

  return (
    <div className="space-y-6 px-4 py-8 max-w-7xl mx-auto">
      <Button variant="outline" onClick={onBack} className="mb-4">
        <ArrowLeft className="mr-2 h-4 w-4" />
        Back to Teams
      </Button>

      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          Course: <span className="font-medium">{course}</span> | Exercise: <span className="font-medium">{exercise}</span>
        </p>
        <div className="flex items-center gap-2">
          {onToggleReviewed && (
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <button
                    onClick={onToggleReviewed}
                    className="flex items-center justify-center h-7 w-7 rounded-md hover:bg-muted transition-colors"
                  >
                    <CircleCheck className={`h-5 w-5 ${team.isReviewed ? 'text-primary fill-primary/20' : 'text-muted-foreground/40'}`} />
                  </button>
                </TooltipTrigger>
                <TooltipContent>
                  <p>{team.isReviewed ? 'Mark as unreviewed' : 'Mark as reviewed'}</p>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          )}
          {canComputeAi && (
            <Button size="sm" variant="outline" onClick={() => computeAiMutation.mutate()} disabled={isAiComputing}>
              {isAiComputing ? <Loader2 className="mr-1.5 h-3 w-3 animate-spin" /> : <Sparkles className="mr-1.5 h-3 w-3" />}
              {isAiComputing ? 'Computing AI...' : hasAiResult ? 'Recompute AI' : 'Compute AI'}
            </Button>
          )}
        </div>
      </div>

      <Card className="shadow-elevated bg-white">
        <CardContent className="pt-6">
          <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-6">
            <div className="space-y-4 flex-1">
              <div className="space-y-1 mb-4">
                <div className="flex items-center gap-2">
                  <h2 className="text-2xl font-bold">{team.teamName}</h2>
                  {artemisRepoUrl && (
                    <a
                      href={artemisRepoUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-muted-foreground hover:text-primary transition-colors"
                      title="Open repository on Artemis"
                    >
                      <ExternalLink className="h-5 w-5" />
                    </a>
                  )}
                </div>
                <p className="text-sm text-muted-foreground">Team Details</p>
              </div>

              <div className="flex items-center gap-2">
                <Users className="h-5 w-5 text-primary" />
                <h3 className="text-xl font-bold">Team Members</h3>
              </div>
              <div className="space-y-2 pl-7">
                {(team.students || []).map((student, index) => (
                  <div key={index} className="space-y-0.5">
                    <p className={`text-lg font-medium ${isAnalysisComplete && (student.commitCount ?? 0) < 10 ? 'text-destructive' : ''}`}>
                      {student.name}
                    </p>
                    {hasStudentMetadata(student) ? (
                      <p className="text-xs text-muted-foreground">
                        {student.commitCount} commits • {student.linesChanged} lines changed (
                        <span className="text-green-600">+{student.linesAdded}</span>{' '}
                        <span className="text-red-600">-{student.linesDeleted}</span>)
                      </p>
                    ) : team.analysisStatus === 'GIT_ANALYZING' || team.analysisStatus === 'DOWNLOADING' ? (
                      <p className="text-xs text-amber-500">Analyzing...</p>
                    ) : team.analysisStatus === 'PENDING' ? (
                      <p className="text-xs text-muted-foreground">Pending analysis</p>
                    ) : null}
                  </div>
                ))}
              </div>
              <div className="flex items-center gap-2 mt-5">
                <ClipboardCheck className="h-5 w-5 text-primary" />
                <h3 className="text-sm font-medium">Tutor: {team.tutor ?? 'Unassigned'}</h3>
              </div>
              {isDevMode && (
                <div className="mt-3 rounded-lg border border-primary/20 bg-primary/5 p-3">
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-2">Dev Mode: Team LLM Tokens</p>
                  <div className="grid grid-cols-2 gap-2 text-sm">
                    <div>
                      <p className="text-muted-foreground text-xs">LLM Calls</p>
                      <p className="font-medium">{(team.llmTokenTotals?.llmCalls ?? 0).toLocaleString()}</p>
                    </div>
                    <div>
                      <p className="text-muted-foreground text-xs">Calls w/ Usage</p>
                      <p className="font-medium">{(team.llmTokenTotals?.callsWithUsage ?? 0).toLocaleString()}</p>
                    </div>
                    <div>
                      <p className="text-muted-foreground text-xs">Prompt</p>
                      <p className="font-medium">{(team.llmTokenTotals?.promptTokens ?? 0).toLocaleString()}</p>
                    </div>
                    <div>
                      <p className="text-muted-foreground text-xs">Completion</p>
                      <p className="font-medium">{(team.llmTokenTotals?.completionTokens ?? 0).toLocaleString()}</p>
                    </div>
                    <div className="col-span-2">
                      <p className="text-muted-foreground text-xs">Total</p>
                      <p className="font-semibold">{(team.llmTokenTotals?.totalTokens ?? 0).toLocaleString()}</p>
                    </div>
                  </div>
                </div>
              )}
              <div className="pt-2">
                <div className="flex flex-wrap items-center gap-2">
                  {isTeamFailed(team) ? (
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <Badge variant="destructive" className="gap-1.5 cursor-help">
                            <AlertTriangle className="h-3 w-3" />
                            Failed
                          </Badge>
                        </TooltipTrigger>
                        <TooltipContent className="max-w-xs">
                          <p>{getFailedReason(team)}</p>
                        </TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                  ) : team.isSuspicious ? (
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <Badge variant="destructive" className="gap-1.5 cursor-help">
                            <AlertTriangle className="h-3 w-3" />
                            Suspicious Behavior Detected
                          </Badge>
                        </TooltipTrigger>
                        <TooltipContent>
                          <p>Suspicious collaboration patterns detected during analysis</p>
                        </TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                  ) : isGitAnalysisComplete ? (
                    <Badge variant="secondary" className="bg-success/10 text-success hover:bg-success/20">
                      Normal Collaboration Pattern
                    </Badge>
                  ) : (
                    <Badge variant="outline" className="gap-1.5 text-muted-foreground border-amber-500/50 bg-amber-500/10">
                      Analyzing...
                    </Badge>
                  )}
                  {pairProgrammingBadgeStatus ? (
                    <PairProgrammingBadge status={pairProgrammingBadgeStatus} verbose={true} />
                  ) : team.analysisStatus !== 'ERROR' && team.analysisStatus !== 'CANCELLED' ? (
                    <Badge variant="outline" className="text-muted-foreground border-amber-500/50 bg-amber-500/10">
                      PP: Pending
                    </Badge>
                  ) : null}
                </div>
              </div>
            </div>

            <div className="flex flex-col items-center md:items-end gap-2">
              {team.isFailed ? (
                <div className="flex items-center justify-center w-32 h-32 rounded-2xl bg-destructive/10 border-2 border-destructive/30">
                  <div className="text-center">
                    <AlertTriangle className="h-8 w-8 text-destructive mx-auto mb-1" />
                    <div className="text-xs text-destructive font-medium">Failed</div>
                  </div>
                </div>
              ) : team.cqi !== undefined ? (
                <div className={`flex items-center justify-center w-32 h-32 rounded-2xl ${getCQIBgColor(team.cqi)}`}>
                  <div className="text-center">
                    <div className={`text-5xl font-bold ${getCQIColor(team.cqi)}`}>{team.cqi}</div>
                    <div className="text-xs text-muted-foreground mt-1">CQI Score</div>
                  </div>
                </div>
              ) : team.analysisStatus === 'AI_ANALYZING' ? (
                <div className="flex items-center justify-center w-32 h-32 rounded-2xl bg-purple-500/10 border-2 border-purple-500/30">
                  <div className="text-center">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-500 mx-auto mb-1"></div>
                    <div className="text-xs text-purple-500">AI Analysis</div>
                    <div className="text-xs text-purple-500">in progress</div>
                  </div>
                </div>
              ) : team.analysisStatus === 'GIT_DONE' ? (
                <div className="flex items-center justify-center w-32 h-32 rounded-2xl bg-blue-500/10 border-2 border-blue-500/30">
                  <div className="text-center">
                    <div className="text-xs text-blue-500">Git Done</div>
                    <div className="text-xs text-blue-500">Waiting for analysis</div>
                  </div>
                </div>
              ) : team.analysisStatus === 'CANCELLED' ? (
                <div className="flex items-center justify-center w-32 h-32 rounded-2xl bg-muted/50 border-2 border-dashed border-muted-foreground/30">
                  <div className="text-center">
                    <Ban className="h-8 w-8 text-muted-foreground mx-auto mb-1" />
                    <div className="text-xs text-muted-foreground">Cancelled</div>
                  </div>
                </div>
              ) : (
                <div className="flex items-center justify-center w-32 h-32 rounded-2xl bg-muted/50 border-2 border-dashed border-muted-foreground/30">
                  <div className="text-center">
                    <AlertTriangle className="h-8 w-8 text-warning mx-auto mb-1" />
                    <div className="text-xs text-muted-foreground">Analysis</div>
                    <div className="text-xs text-muted-foreground">Incomplete</div>
                  </div>
                </div>
              )}
              <p className="text-sm text-muted-foreground text-center md:text-right">
                {team.isFailed
                  ? 'Not calculated — failed requirements'
                  : team.cqi !== undefined
                    ? 'Collaboration Quality Index'
                    : team.analysisStatus === 'AI_ANALYZING'
                      ? 'Calculating CQI...'
                      : team.analysisStatus === 'GIT_DONE'
                        ? 'Waiting for analysis'
                        : team.analysisStatus === 'CANCELLED'
                          ? 'Analysis was cancelled'
                          : team.analysisStatus === 'PENDING' ||
                              team.analysisStatus === 'DOWNLOADING' ||
                              team.analysisStatus === 'GIT_ANALYZING'
                            ? 'CQI score still being determined'
                            : 'CQI not yet calculated'}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      {courseAverages && (
        <Card className="p-6 shadow-card" data-testid="course-comparison-card">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-lg font-semibold">Course Comparison</h3>
              <p className="text-sm text-muted-foreground">
                Based on {courseAverages.analyzedTeams} analyzed team{courseAverages.analyzedTeams !== 1 ? 's' : ''}
              </p>
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">Commits</p>
              <p className="text-2xl font-bold">{computeBasicMetrics(team.students).totalCommits || '—'}</p>
              <p className="text-xs text-muted-foreground">
                Course avg: {courseAverages.gitAnalyzedTeams > 0 ? courseAverages.avgCommits : 'Pending'}
              </p>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">Lines</p>
              <p className="text-2xl font-bold">{computeBasicMetrics(team.students).totalLines.toLocaleString() || '—'}</p>
              <p className="text-xs text-muted-foreground">
                Course avg: {courseAverages.gitAnalyzedTeams > 0 ? courseAverages.avgLines.toLocaleString() : 'Pending'}
              </p>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">CQI</p>
              <p
                className={`text-2xl font-bold ${team.isFailed ? 'text-destructive' : team.cqi !== undefined ? getCQIColor(team.cqi) : ''}`}
              >
                {team.isFailed ? 'Failed' : (team.cqi ?? '—')}
              </p>
              <p className="text-xs text-muted-foreground">
                Course avg: {courseAverages.analyzedTeams > 0 ? courseAverages.avgCQI : 'Pending'}
              </p>
            </div>
          </div>
        </Card>
      )}

      <div className="space-y-4">
        <div>
          <h3 className="text-2xl font-bold mb-2">Detailed Metrics</h3>
          <p className="text-muted-foreground">
            The CQI is composed of four weighted sub-indices that measure different aspects of team collaboration
          </p>
        </div>

        {team.isFailed ? (
          <Card className="p-8 flex items-center justify-center">
            <div className="flex flex-col items-center gap-3 text-muted-foreground">
              <AlertTriangle className="h-8 w-8 text-destructive" />
              <p className="text-sm font-medium">Metrics not computed — team did not meet minimum requirements.</p>
            </div>
          </Card>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {metricsToShow.map((metric, index) => (
              <MetricCard key={index} metric={metric} />
            ))}
          </div>
        )}
      </div>

      {/* Filter Summary */}
      {team.cqiDetails?.filterSummary && (
        <Card className="shadow-card">
          <CardHeader>
            <div className="flex items-center gap-2">
              <Filter className="h-5 w-5 text-primary" />
              <CardTitle className="text-lg">Pre-Filter Summary</CardTitle>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground mb-3">
              <span className="font-bold">{team.cqiDetails.filterSummary.productiveCommits ?? 0}</span> of{' '}
              <span className="font-bold">{team.cqiDetails.filterSummary.totalCommits ?? 0}</span> commits analyzed
              {(team.cqiDetails.filterSummary.totalCommits ?? 0) > 0 && (
                <span className="text-xs ml-1">
                  (
                  {Math.round(
                    ((team.cqiDetails.filterSummary.productiveCommits ?? 0) / (team.cqiDetails.filterSummary.totalCommits ?? 1)) * 100,
                  )}
                  % kept)
                </span>
              )}
            </p>
            <div className="grid grid-cols-2 gap-2 text-sm">
              {(team.cqiDetails.filterSummary.mergeCount ?? 0) > 0 && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Merge commits:</span>
                  <span>{team.cqiDetails.filterSummary.mergeCount}</span>
                </div>
              )}
              {(team.cqiDetails.filterSummary.revertCount ?? 0) > 0 && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Revert commits:</span>
                  <span>{team.cqiDetails.filterSummary.revertCount}</span>
                </div>
              )}
              {(team.cqiDetails.filterSummary.trivialCount ?? 0) > 0 && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Trivial commits:</span>
                  <span>{team.cqiDetails.filterSummary.trivialCount}</span>
                </div>
              )}
              {(team.cqiDetails.filterSummary.formatOnlyCount ?? 0) > 0 && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Format-only:</span>
                  <span>{team.cqiDetails.filterSummary.formatOnlyCount}</span>
                </div>
              )}
              {(team.cqiDetails.filterSummary.autoGeneratedCount ?? 0) > 0 && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Auto-generated:</span>
                  <span>{team.cqiDetails.filterSummary.autoGeneratedCount}</span>
                </div>
              )}
              {(team.cqiDetails.filterSummary.emptyCount ?? 0) > 0 && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Empty commits:</span>
                  <span>{team.cqiDetails.filterSummary.emptyCount}</span>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {/* AI Analysis Feed */}
      <div className="space-y-4">
        <div>
          <h3 className="text-2xl font-bold mb-2">AI Analysis Feed</h3>
          <p className="text-muted-foreground">See exactly how the AI analyzed each commit or group of commits</p>
        </div>

        {team.analysisHistory && team.analysisHistory.length > 0 ? (
          <>
            <ErrorListPanel
              errors={
                team.analysisHistory
                  ?.filter((chunk: AnalyzedChunkDTO) => chunk.isError && chunk.errorMessage)
                  .map((chunk: AnalyzedChunkDTO) => ({
                    id: chunk.id ?? '',
                    authorEmail: chunk.authorEmail ?? '',
                    timestamp: chunk.timestamp ?? '',
                    errorMessage: chunk.errorMessage!,
                    commitShas: chunk.commitShas ?? [],
                  })) || []
              }
            />

            <OrphanCommitsPanel
              commits={team.orphanCommits || []}
              analysisHistory={team.analysisHistory}
              students={team.students ?? []}
              exerciseId={exercise}
              teamParticipationId={String(team.teamId)}
              emailMappings={emailMappings}
              onMappingChange={handleMappingChange}
              templateAuthorEmails={templateAuthorEmails}
            />

            <AnalysisFeed
              chunks={team.analysisHistory || []}
              isDevMode={isDevMode}
              dismissedEmails={dismissedEmails}
              onUndoDismiss={handleUndoDismiss}
              assignedEmails={assignedEmails}
              onUndoAssignment={handleUndoAssignment}
            />
          </>
        ) : team.isFailed ? (
          <Card className="p-8 flex items-center justify-center">
            <div className="flex flex-col items-center gap-3 text-muted-foreground">
              <AlertTriangle className="h-8 w-8 text-destructive" />
              <p className="text-sm font-medium">AI analysis skipped — team did not meet minimum requirements.</p>
            </div>
          </Card>
        ) : team.analysisStatus === 'CANCELLED' ? (
          <Card className="p-8 flex items-center justify-center">
            <div className="flex flex-col items-center gap-3 text-muted-foreground">
              <Ban className="h-8 w-8" />
              <p className="text-sm font-medium">AI analysis was cancelled before this team was processed.</p>
            </div>
          </Card>
        ) : isAiComputing ? (
          <Card className="p-8 flex items-center justify-center">
            <div className="flex flex-col items-center gap-3 text-muted-foreground">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
              <p className="text-sm font-medium">Computing AI analysis...</p>
            </div>
          </Card>
        ) : isSimpleMode && !hasAiResult ? (
          <Card className="p-8 flex items-center justify-center">
            <div className="flex flex-col items-center gap-3 text-muted-foreground">
              <Sparkles className="h-8 w-8" />
              <p className="text-sm font-medium">AI analysis is not included in Simple mode.</p>
              <p className="text-xs">Use the &quot;Compute AI&quot; button above to run AI analysis for this team.</p>
            </div>
          </Card>
        ) : (
          <Card className="p-8 flex items-center justify-center">
            <div className="flex flex-col items-center gap-3 text-muted-foreground">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
              <p className="text-sm font-medium">Computing AI analysis...</p>
            </div>
          </Card>
        )}
      </div>
    </div>
  );
};

export default TeamDetail;

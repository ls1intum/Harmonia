import type { Team } from '@/types/team';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { AlertTriangle, ArrowLeft, Play, Square, RefreshCw, Trash2, CodeXml } from 'lucide-react';
import { useState, useMemo } from 'react';
import { SortableHeader, type SortColumn } from '@/components/SortableHeader.tsx';
import { StatusFilterButton, type StatusFilter } from '@/components/StatusFilterButton.tsx';
import { ActivityLog, type AnalysisStatus } from '@/components/ActivityLog';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { readDevModeFromStorage, writeDevModeToStorage } from '@/lib/devMode';
import ExportButton from '@/components/ExportButton';

interface TeamsListProps {
  teams: Team[];
  onTeamSelect: (team: Team) => void;
  onBackToHome: () => void;
  onStart: () => void;
  onCancel: () => void;
  onRecompute: () => void;
  onClear: (type: 'db' | 'files' | 'both') => void;
  course: string;
  exercise: string;
  analysisStatus: AnalysisStatus;
  isLoading?: boolean;
  isStarting?: boolean;
  isCancelling?: boolean;
  isRecomputing?: boolean;
  isClearing?: boolean;
}

const TeamsList = ({
  teams,
  onTeamSelect,
  onBackToHome,
  onStart,
  onCancel,
  onRecompute,
  onClear,
  course,
  exercise,
  analysisStatus,
  isLoading = false,
  isStarting = false,
  isCancelling = false,
  isRecomputing = false,
  isClearing = false,
}: TeamsListProps) => {
  const [sortColumn, setSortColumn] = useState<SortColumn | null>(null);
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [clearDialogOpen, setClearDialogOpen] = useState(false);
  const [clearType, setClearType] = useState<'db' | 'files' | 'both'>('both');
  const [isDevMode, setIsDevMode] = useState<boolean>(readDevModeFromStorage);

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

  const handleHeaderClick = (column: SortColumn) => {
    if (sortColumn !== column) {
      setSortColumn(column);
      setSortDirection('asc');
    } else if (sortDirection === 'asc') {
      setSortDirection('desc');
    } else {
      setSortColumn(null);
    }
  };

  // Helper to determine if a team is 'failed' (any student with <10 commits)
  const isTeamFailed = (team: Team) => {
    return (team.students || []).some(s => (s.commitCount ?? 0) < 10);
  };

  // Get tooltip text explaining why a team failed
  const getFailedReason = (team: Team) => {
    const failedStudents = (team.students || []).filter(s => (s.commitCount ?? 0) < 10);
    if (failedStudents.length === 0) return '';
    return `Failed: ${failedStudents.map(s => `${s.name} has only ${s.commitCount ?? 0} commits`).join(', ')}. Minimum required: 10 commits per member.`;
  };

  // Get priority for analysis status (lower = shown first)
  const getStatusPriority = (status: string | undefined): number => {
    switch (status) {
      case 'DONE':
        return 0; // Fully completed - show first
      case 'AI_ANALYZING':
        return 1; // AI analysis in progress
      case 'GIT_DONE':
        return 2; // Git analysis done, waiting for AI
      case 'GIT_ANALYZING':
      case 'ANALYZING':
        return 3; // Currently analyzing
      case 'PENDING':
      case 'DOWNLOADING':
        return 4; // Waiting to be analyzed
      case 'ERROR':
        return 5; // Failed
      case 'CANCELLED':
        return 6; // Cancelled
      default:
        return 7; // Unknown
    }
  };

  const sortedAndFilteredTeams = useMemo(() => {
    let filtered = [...teams];

    // Apply status filter
    if (statusFilter !== 'all') {
      if (statusFilter === 'failed') {
        filtered = filtered.filter(team => isTeamFailed(team));
      } else if (statusFilter === 'suspicious') {
        filtered = filtered.filter(team => team.isSuspicious);
      } else if (statusFilter === 'normal') {
        filtered = filtered.filter(team => !team.isSuspicious && !isTeamFailed(team));
      }
    }

    // First, sort by analysis status priority (ANALYZING > PENDING > DONE)
    filtered.sort((a, b) => {
      const aPriority = getStatusPriority(a.analysisStatus);
      const bPriority = getStatusPriority(b.analysisStatus);
      if (aPriority !== bPriority) {
        return aPriority - bPriority;
      }
      // If same priority, maintain original order or apply column sort
      return 0;
    });

    // Then apply column sorting within each status group
    if (sortColumn) {
      // Create a stable sort that preserves status priority
      const statusGroups = new Map<number, Team[]>();
      filtered.forEach(team => {
        const priority = getStatusPriority(team.analysisStatus);
        if (!statusGroups.has(priority)) {
          statusGroups.set(priority, []);
        }
        statusGroups.get(priority)!.push(team);
      });

      // Sort within each group
      statusGroups.forEach(group => {
        group.sort((a, b) => {
          let comparison = 0;
          if (sortColumn === 'name') {
            comparison = a.teamName.localeCompare(b.teamName);
          } else if (sortColumn === 'commitCount') {
            const aCommits = a.basicMetrics?.totalCommits || 0;
            const bCommits = b.basicMetrics?.totalCommits || 0;
            comparison = aCommits - bCommits;
          } else if (sortColumn === 'cqi') {
            const aCqi = a.cqi || 0;
            const bCqi = b.cqi || 0;
            comparison = aCqi - bCqi;
          }
          return sortDirection === 'asc' ? comparison : -comparison;
        });
      });

      // Reconstruct filtered array in priority order
      filtered = [];
      [0, 1, 2, 3, 4, 5, 6, 7].forEach(priority => {
        const group = statusGroups.get(priority);
        if (group) {
          filtered.push(...group);
        }
      });
    }

    return filtered;
  }, [teams, sortColumn, sortDirection, statusFilter]);

  const courseAverages = useMemo(() => {
    if (teams.length === 0) return null;

    // Only include teams with calculated CQI in the CQI average
    const teamsWithCQI = teams.filter(team => team.cqi !== undefined);
    const totalCQI = teamsWithCQI.reduce((sum, team) => sum + (team.cqi ?? 0), 0);

    // Include teams with git metrics (GIT_DONE, AI_ANALYZING, or DONE) in commit/line averages
    const teamsWithGitMetrics = teams.filter(
      team => team.analysisStatus === 'GIT_DONE' || team.analysisStatus === 'AI_ANALYZING' || team.analysisStatus === 'DONE',
    );
    const totalCommits = teamsWithGitMetrics.reduce((sum, team) => sum + (team.basicMetrics?.totalCommits || 0), 0);
    const totalLines = teamsWithGitMetrics.reduce((sum, team) => sum + (team.basicMetrics?.totalLines || 0), 0);
    const suspiciousCount = teams.filter(team => team.isSuspicious === true).length;

    return {
      avgCQI: teamsWithCQI.length > 0 ? Math.round(totalCQI / teamsWithCQI.length) : 0,
      avgCommits: teamsWithGitMetrics.length > 0 ? Math.round(totalCommits / teamsWithGitMetrics.length) : 0,
      avgLines: teamsWithGitMetrics.length > 0 ? Math.round(totalLines / teamsWithGitMetrics.length) : 0,
      suspiciousPercentage: Math.round((suspiciousCount / teams.length) * 100),
      totalTeams: teams.length,
      analyzedTeams: teamsWithCQI.length,
      gitAnalyzedTeams: teamsWithGitMetrics.length,
    };
  }, [teams]);

  const renderActionButton = () => {
    if (isLoading) {
      return (
        <Button disabled variant="outline">
          <RefreshCw className="h-4 w-4 animate-spin" />
          Loading...
        </Button>
      );
    }

    switch (analysisStatus.state) {
      case 'IDLE':
      case 'CANCELLED':
        return (
          <Button onClick={onStart} disabled={isStarting || isClearing}>
            {isStarting ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
            {isStarting ? 'Starting...' : 'Start Analysis'}
          </Button>
        );
      case 'RUNNING':
        return (
          <Button variant="destructive" onClick={onCancel} disabled={isCancelling}>
            {isCancelling ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Square className="h-4 w-4" />}
            {isCancelling ? 'Cancelling...' : 'Cancel'}
          </Button>
        );
      case 'DONE':
      case 'ERROR':
        return (
          <Button variant="secondary" onClick={onRecompute} disabled={isRecomputing || isClearing}>
            <RefreshCw className={`h-4 w-4 ${isRecomputing ? 'animate-spin' : ''}`} />
            {isRecomputing ? 'Recomputing...' : 'Force Recompute'}
          </Button>
        );
    }
  };

  const handleClearClick = (type: 'db' | 'files' | 'both') => {
    setClearType(type);
    setClearDialogOpen(true);
  };

  const toggleDevMode = () => {
    const next = !isDevMode;
    setIsDevMode(next);
    writeDevModeToStorage(next);
  };

  const overallTokenTotals = useMemo(
    () =>
      teams.reduce(
        (acc, team) => {
          const totals = team.llmTokenTotals;
          if (!totals) {
            return acc;
          }
          return {
            llmCalls: acc.llmCalls + (totals.llmCalls ?? 0),
            callsWithUsage: acc.callsWithUsage + (totals.callsWithUsage ?? 0),
            promptTokens: acc.promptTokens + (totals.promptTokens ?? 0),
            completionTokens: acc.completionTokens + (totals.completionTokens ?? 0),
            totalTokens: acc.totalTokens + (totals.totalTokens ?? 0),
          };
        },
        {
          llmCalls: 0,
          callsWithUsage: 0,
          promptTokens: 0,
          completionTokens: 0,
          totalTokens: 0,
        },
      ),
    [teams],
  );

  return (
    <div className="space-y-6 px-4 py-8 max-w-7xl mx-auto">
      <Button variant="ghost" onClick={onBackToHome} className="mb-4 text-muted-foreground hover:text-accent-foreground hover:bg-accent">
        <ArrowLeft className="mr-2 h-4 w-4" />
        Back to Home
      </Button>

      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="space-y-2">
          <h2 className="text-3xl font-bold">Teams Overview</h2>
          <p className="text-muted-foreground">
            Course: <span className="font-medium">{course}</span> | Exercise: <span className="font-medium">{exercise}</span>
          </p>
          <p className="text-muted-foreground text-sm">Click on any team to view detailed collaboration metrics</p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="ghost"
            onClick={toggleDevMode}
            aria-pressed={isDevMode}
            aria-label={isDevMode ? 'Disable dev mode' : 'Enable dev mode'}
            title="Dev Mode"
            className={`group gap-0 ${isDevMode ? 'bg-accent text-accent-foreground hover:bg-accent/80' : ''}`}
          >
            <CodeXml className="h-4 w-4" />
            <span
              className={`overflow-hidden whitespace-nowrap transition-all duration-200 ${
                isDevMode
                  ? 'max-w-24 opacity-100 ml-2'
                  : 'max-w-0 opacity-0 ml-0 group-hover:max-w-24 group-hover:opacity-100 group-hover:ml-2 group-focus-visible:max-w-24 group-focus-visible:opacity-100 group-focus-visible:ml-2'
              }`}
            >
              Dev Mode
            </span>
          </Button>
          {renderActionButton()}
          <ExportButton exerciseId={exercise} disabled={teams.length === 0 || analysisStatus.state === 'RUNNING'} />
          <Button
            variant="outline"
            onClick={() => handleClearClick('both')}
            disabled={analysisStatus.state === 'RUNNING' || isClearing || isStarting || isRecomputing}
          >
            {isClearing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
            {isClearing ? 'Clearing...' : 'Clear Data'}
          </Button>
        </div>
      </div>

      <ActivityLog status={analysisStatus} />

      <ConfirmationDialog
        open={clearDialogOpen}
        onOpenChange={setClearDialogOpen}
        title="Clear Data"
        description={`This will permanently delete ${clearType === 'both' ? 'database records and repository files' : clearType === 'db' ? 'database records' : 'repository files'}. This action cannot be undone.`}
        confirmLabel="Clear"
        variant="destructive"
        onConfirm={() => onClear(clearType)}
      />

      {courseAverages && (
        <Card className="p-6 shadow-card">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-semibold">Course Averages</h3>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">Average Commits</p>
              {courseAverages.gitAnalyzedTeams === 0 ? (
                <p className="text-2xl font-bold text-amber-500">Pending</p>
              ) : courseAverages.gitAnalyzedTeams < courseAverages.totalTeams ? (
                <p className="text-2xl font-bold text-amber-500">{courseAverages.avgCommits}</p>
              ) : (
                <p className="text-3xl font-bold text-foreground">{courseAverages.avgCommits}</p>
              )}
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">Average Lines</p>
              {courseAverages.gitAnalyzedTeams === 0 ? (
                <p className="text-2xl font-bold text-amber-500">Pending</p>
              ) : courseAverages.gitAnalyzedTeams < courseAverages.totalTeams ? (
                <p className="text-2xl font-bold text-amber-500">{courseAverages.avgLines.toLocaleString()}</p>
              ) : (
                <p className="text-3xl font-bold text-foreground">{courseAverages.avgLines.toLocaleString()}</p>
              )}
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">Average CQI</p>
              <div className="flex items-baseline gap-2">
                {courseAverages.analyzedTeams === 0 ? (
                  <p className="text-2xl font-bold text-amber-500">Pending</p>
                ) : courseAverages.analyzedTeams < courseAverages.totalTeams ? (
                  <p className="text-2xl font-bold text-amber-500">{courseAverages.avgCQI}</p>
                ) : (
                  <>
                    <p className={`text-3xl font-bold ${getCQIColor(courseAverages.avgCQI)}`}>{courseAverages.avgCQI}</p>
                    <span className="text-sm text-muted-foreground">/ 100</span>
                  </>
                )}
              </div>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">Suspicious Teams</p>
              <div className="flex items-baseline gap-2">
                {courseAverages.analyzedTeams === 0 ? (
                  <p className="text-2xl font-bold text-amber-500">Pending</p>
                ) : courseAverages.analyzedTeams < courseAverages.totalTeams ? (
                  <p className="text-2xl font-bold text-amber-500">{courseAverages.suspiciousPercentage}%</p>
                ) : (
                  <>
                    <p className={`text-3xl font-bold ${courseAverages.suspiciousPercentage > 30 ? 'text-destructive' : 'text-success'}`}>
                      {courseAverages.suspiciousPercentage}%
                    </p>
                    <span className="text-sm text-muted-foreground">
                      ({teams.filter(t => t.isSuspicious).length} of {courseAverages.totalTeams})
                    </span>
                  </>
                )}
              </div>
            </div>
          </div>
        </Card>
      )}

      {isDevMode && (
        <Card className="p-6 shadow-card border-primary/20">
          <div className="mb-4">
            <h3 className="text-lg font-semibold">LLM Tokens (Overall)</h3>
            <p className="text-sm text-muted-foreground">Aggregated over all teams in this exercise</p>
          </div>
          <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">LLM Calls</p>
              <p className="text-xl font-semibold">{overallTokenTotals.llmCalls.toLocaleString()}</p>
            </div>
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">Calls w/ Usage</p>
              <p className="text-xl font-semibold">{overallTokenTotals.callsWithUsage.toLocaleString()}</p>
            </div>
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">Prompt</p>
              <p className="text-xl font-semibold">{overallTokenTotals.promptTokens.toLocaleString()}</p>
            </div>
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">Completion</p>
              <p className="text-xl font-semibold">{overallTokenTotals.completionTokens.toLocaleString()}</p>
            </div>
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">Total</p>
              <p className="text-xl font-semibold">{overallTokenTotals.totalTokens.toLocaleString()}</p>
            </div>
          </div>
        </Card>
      )}

      <Card className="overflow-hidden shadow-card">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-muted/50 border-b">
              <tr>
                <th className="text-left py-4 px-6 font-semibold text-sm">
                  <SortableHeader
                    column="name"
                    label="Team Name"
                    sortColumn={sortColumn}
                    sortDirection={sortDirection}
                    handleHeaderClick={handleHeaderClick}
                  />
                </th>
                <th className="text-left py-4 px-6 font-semibold text-sm">Members</th>
                <th className="text-left py-4 px-6 font-semibold text-sm">
                  <SortableHeader
                    column="commitCount"
                    label="Commits"
                    sortColumn={sortColumn}
                    sortDirection={sortDirection}
                    handleHeaderClick={handleHeaderClick}
                  />
                </th>
                <th className="text-left py-4 px-6 font-semibold text-sm">
                  <SortableHeader
                    column="cqi"
                    label="CQI Score"
                    sortColumn={sortColumn}
                    sortDirection={sortDirection}
                    handleHeaderClick={handleHeaderClick}
                  />
                </th>
                {isDevMode && <th className="text-left py-4 px-6 font-semibold text-sm">LLM Tokens</th>}
                <th className="text-left py-4 px-6 font-semibold text-sm">
                  <StatusFilterButton statusFilter={statusFilter} setStatusFilter={setStatusFilter} />
                </th>
              </tr>
            </thead>
            <tbody>
              {sortedAndFilteredTeams.map(team => (
                <tr
                  key={team.id}
                  onClick={() => onTeamSelect(team)}
                  className="border-b last:border-b-0 hover:bg-muted/30 cursor-pointer transition-colors"
                >
                  <td className="py-4 px-6">
                    <p className="font-semibold">{team.teamName.replace('Team ', '')}</p>
                  </td>
                  <td className="py-4 px-6">
                    <div className="space-y-1">
                      {team.students.map((student, idx) => (
                        <p
                          key={idx}
                          className={`text-sm ${(team.analysisStatus === 'DONE' || team.analysisStatus === 'GIT_DONE' || team.analysisStatus === 'AI_ANALYZING') && (student.commitCount ?? 0) < 10 ? 'text-destructive' : ''}`}
                        >
                          {student.name}
                          {(team.analysisStatus === 'DONE' ||
                            team.analysisStatus === 'GIT_DONE' ||
                            team.analysisStatus === 'AI_ANALYZING') &&
                            student.commitCount !== undefined && (
                              <span className={(student.commitCount ?? 0) < 10 ? 'text-destructive' : 'text-muted-foreground'}>
                                {' '}
                                ({student.commitCount} commits)
                              </span>
                            )}
                        </p>
                      ))}
                    </div>
                  </td>
                  <td className="py-4 px-6">
                    {(team.analysisStatus === 'DONE' || team.analysisStatus === 'GIT_DONE' || team.analysisStatus === 'AI_ANALYZING') &&
                    team.basicMetrics ? (
                      <div className="space-y-1">
                        <p className="font-medium">{team.basicMetrics.totalCommits}</p>
                        <p className="text-xs text-muted-foreground">{team.basicMetrics.totalLines} lines</p>
                      </div>
                    ) : team.analysisStatus === 'PENDING' ||
                      team.analysisStatus === 'DOWNLOADING' ||
                      team.analysisStatus === 'GIT_ANALYZING' ||
                      team.analysisStatus === 'ANALYZING' ? (
                      <div className="flex items-center gap-2 text-muted-foreground">
                        <span className="text-sm">{team.analysisStatus === 'GIT_ANALYZING' ? 'Analyzing...' : 'Pending'}</span>
                      </div>
                    ) : team.analysisStatus === 'ERROR' ? (
                      <span className="text-sm text-destructive">—</span>
                    ) : (
                      <span className="text-sm text-muted-foreground">—</span>
                    )}
                  </td>
                  <td className="py-4 px-6">
                    {team.analysisStatus === 'DONE' && team.cqi !== undefined ? (
                      <div className="flex items-center gap-3">
                        <div className={`flex items-center justify-center w-16 h-16 rounded-lg ${getCQIBgColor(team.cqi)}`}>
                          <span className={`text-2xl font-bold ${getCQIColor(team.cqi)}`}>{team.cqi}</span>
                        </div>
                        <div className="text-xs text-muted-foreground">out of 100</div>
                      </div>
                    ) : team.analysisStatus === 'AI_ANALYZING' ? (
                      <div className="flex items-center gap-2 text-blue-500">
                        <span className="text-sm">AI Analyzing...</span>
                      </div>
                    ) : team.analysisStatus === 'GIT_DONE' ? (
                      <div className="flex items-center gap-2 text-muted-foreground">
                        <span className="text-sm">Waiting for AI</span>
                      </div>
                    ) : team.analysisStatus === 'ERROR' ? (
                      <span className="text-sm text-destructive">Analysis Failed</span>
                    ) : team.analysisStatus === 'CANCELLED' ? (
                      <span className="text-sm text-muted-foreground">Cancelled</span>
                    ) : (
                      <div className="flex items-center gap-2 text-muted-foreground">
                        <span className="text-sm">
                          {team.analysisStatus === 'PENDING' || team.analysisStatus === 'DOWNLOADING'
                            ? 'Pending'
                            : team.analysisStatus === 'GIT_ANALYZING'
                              ? 'Git Analysis...'
                              : 'Analyzing...'}
                        </span>
                      </div>
                    )}
                  </td>
                  {isDevMode && (
                    <td className="py-4 px-6">
                      {team.llmTokenTotals ? (
                        <div className="space-y-1">
                          <p className="font-medium">{(team.llmTokenTotals.totalTokens ?? 0).toLocaleString()}</p>
                          <p className="text-xs text-muted-foreground">
                            in {(team.llmTokenTotals.llmCalls ?? 0).toLocaleString()} calls
                            {(team.llmTokenTotals.callsWithUsage ?? 0) < (team.llmTokenTotals.llmCalls ?? 0) &&
                              ` (${(team.llmTokenTotals.callsWithUsage ?? 0).toLocaleString()} with usage)`}
                          </p>
                        </div>
                      ) : (
                        <span className="text-sm text-muted-foreground">—</span>
                      )}
                    </td>
                  )}
                  <td className="py-4 px-6">
                    {/* Show Failed badge if any student has <10 commits (after git analysis) */}
                    {(team.analysisStatus === 'GIT_DONE' || team.analysisStatus === 'AI_ANALYZING' || team.analysisStatus === 'DONE') &&
                    isTeamFailed(team) ? (
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
                    ) : team.analysisStatus === 'DONE' && team.isSuspicious !== undefined ? (
                      team.isSuspicious ? (
                        <TooltipProvider>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <Badge variant="destructive" className="gap-1.5 cursor-help">
                                <AlertTriangle className="h-3 w-3" />
                                Suspicious
                              </Badge>
                            </TooltipTrigger>
                            <TooltipContent>
                              <p>Suspicious collaboration patterns detected during analysis</p>
                            </TooltipContent>
                          </Tooltip>
                        </TooltipProvider>
                      ) : (
                        <Badge variant="secondary" className="bg-success/10 text-success hover:bg-success/20">
                          Normal
                        </Badge>
                      )
                    ) : team.analysisStatus === 'ERROR' ? (
                      <Badge variant="destructive" className="gap-1.5">
                        Error
                      </Badge>
                    ) : team.analysisStatus === 'CANCELLED' ? (
                      <Badge variant="outline" className="gap-1.5 text-muted-foreground">
                        Cancelled
                      </Badge>
                    ) : team.analysisStatus === 'GIT_DONE' || team.analysisStatus === 'AI_ANALYZING' ? (
                      <Badge variant="outline" className="gap-1.5 text-blue-500 border-blue-500/50 bg-blue-500/10">
                        {team.analysisStatus === 'AI_ANALYZING' ? 'AI Analysis' : 'Git Done'}
                      </Badge>
                    ) : (
                      <Badge variant="outline" className="gap-1.5 text-muted-foreground border-amber-500/50 bg-amber-500/10">
                        {team.analysisStatus === 'PENDING' || team.analysisStatus === 'DOWNLOADING'
                          ? 'Pending'
                          : team.analysisStatus === 'GIT_ANALYZING'
                            ? 'Git Analysis'
                            : 'Analyzing'}
                      </Badge>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};

export default TeamsList;

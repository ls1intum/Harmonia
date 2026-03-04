import type { TemplateAuthorInfo, TeamDTO } from '@/data/dataLoaders';
import type { CourseAverages } from '@/lib/courseAverages';
import { computeBasicMetrics } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  AlertTriangle,
  ArrowLeft,
  Play,
  Square,
  RefreshCw,
  Trash2,
  CodeXml,
  GitBranch,
  Pencil,
  ChevronDown,
  Search,
  X,
} from 'lucide-react';
import { Input } from '@/components/ui/input';
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogFooter,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogCancel,
  AlertDialogAction,
} from '@/components/ui/alert-dialog';
import { useState, useMemo } from 'react';
import { SortableHeader, type SortColumn } from '@/components/SortableHeader.tsx';
import { StatusFilterButton, type StatusFilter } from '@/components/StatusFilterButton.tsx';
import { ActivityLog } from '@/components/ActivityLog';
import type { AnalysisMode, AnalysisStatus } from '@/hooks/useAnalysisStatus';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { readDevModeFromStorage, writeDevModeToStorage } from '@/lib/devMode';
import ExportButton from '@/components/ExportButton';
import FileUpload from '@/components/FileUpload';
import { getFailedReason } from '@/lib/utils';
import PairProgrammingBadge from '@/components/PairProgrammingBadge';
import {
  getPairProgrammingBadgeStatus,
  hasValidPairProgrammingAttendanceData,
  type PairProgrammingAttendanceMap,
  type PairProgrammingBadgeStatus,
} from '@/lib/pairProgramming';

interface TeamsListProps {
  teams: TeamDTO[];
  courseAverages: CourseAverages | null;
  onTeamSelect: (team: TeamDTO, pairProgrammingBadgeStatus: PairProgrammingBadgeStatus | null) => void;
  onBackToHome: () => void;
  onStart: (mode: AnalysisMode) => void;
  onCancel: () => void;
  onRecompute: (mode: AnalysisMode) => void;
  onClear: (type: 'db' | 'files' | 'both', clearMappings?: boolean) => void;
  course: string;
  exercise: string;
  analysisStatus: AnalysisStatus;
  pairProgrammingEnabled: boolean;
  attendanceFile: File | null;
  uploadedAttendanceFileName: string | null;
  pairProgrammingAttendanceByTeamName: PairProgrammingAttendanceMap;
  onAttendanceFileSelect: (file: File | null) => void;
  onAttendanceUpload: () => void;
  onRemoveUploadedAttendanceFile: () => void;
  templateAuthors: TemplateAuthorInfo[];
  templateAuthorCandidates: string[] | null;
  onTemplateAuthorsSet: (emails: string[]) => void;
  onTemplateAuthorsRemove: () => void;
  isLoading?: boolean;
  isStarting?: boolean;
  isCancelling?: boolean;
  isRecomputing?: boolean;
  isClearing?: boolean;
  isAttendanceUploading?: boolean;
  isAttendanceClearing?: boolean;
}

/**
 * Presentational list of teams with sorting, filtering, search,
 * attendance upload, template-author management, and analysis controls.
 */
const TeamsList = ({
  teams,
  courseAverages,
  onTeamSelect,
  onBackToHome,
  onStart,
  onCancel,
  onRecompute,
  onClear,
  course,
  exercise,
  analysisStatus,
  pairProgrammingEnabled,
  attendanceFile,
  uploadedAttendanceFileName,
  pairProgrammingAttendanceByTeamName,
  onAttendanceFileSelect,
  onAttendanceUpload,
  onRemoveUploadedAttendanceFile,
  templateAuthors,
  templateAuthorCandidates,
  onTemplateAuthorsSet,
  onTemplateAuthorsRemove,
  isLoading = false,
  isStarting = false,
  isCancelling = false,
  isRecomputing = false,
  isClearing = false,
  isAttendanceUploading = false,
  isAttendanceClearing = false,
}: TeamsListProps) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [sortColumn, setSortColumn] = useState<SortColumn | null>(null);
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [clearDialogOpen, setClearDialogOpen] = useState(false);
  const [clearType, setClearType] = useState<'db' | 'files' | 'both'>('both');
  const [clearMappings, setClearMappings] = useState(false);
  const [removeAttendanceDialogOpen, setRemoveAttendanceDialogOpen] = useState(false);
  const [isDevMode, setIsDevMode] = useState<boolean>(readDevModeFromStorage);
  const [startWithoutAttendanceDialogOpen, setStartWithoutAttendanceDialogOpen] = useState(false);
  const [pendingStartMode, setPendingStartMode] = useState<AnalysisMode>('FULL');
  const [pendingStartAction, setPendingStartAction] = useState<((mode: AnalysisMode) => void) | null>(null);
  const [templateAuthorDialogOpen, setTemplateAuthorDialogOpen] = useState(false);
  const [templateAuthorEmails, setTemplateAuthorEmails] = useState<string[]>([]);
  const [newEmailInput, setNewEmailInput] = useState('');
  const hasUploadedAttendanceDocument = !!uploadedAttendanceFileName;

  const openTemplateAuthorDialog = () => {
    setTemplateAuthorEmails(templateAuthors.map(a => a.email));
    setNewEmailInput('');
    setTemplateAuthorDialogOpen(true);
  };

  const addEmailToList = () => {
    const email = newEmailInput.trim().toLowerCase();
    if (email && !templateAuthorEmails.includes(email)) {
      setTemplateAuthorEmails(prev => prev.concat(email));
      setNewEmailInput('');
    }
  };

  const removeEmailFromList = (email: string) => {
    setTemplateAuthorEmails(prev => prev.filter(e => e !== email));
  };

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
  const isTeamFailed = (team: TeamDTO) => {
    return (team.students || []).some(s => (s.commitCount ?? 0) < 10);
  };

  const renderAnalysisStatusBadge = (team: TeamDTO) => {
    if (
      (team.analysisStatus === 'GIT_DONE' || team.analysisStatus === 'AI_ANALYZING' || team.analysisStatus === 'DONE') &&
      isTeamFailed(team)
    ) {
      return (
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
      );
    }

    if (team.analysisStatus === 'DONE' && team.isSuspicious !== undefined) {
      if (team.isSuspicious) {
        return (
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
        );
      }

      return (
        <Badge variant="secondary" className="bg-success/10 text-success hover:bg-success/20">
          Normal
        </Badge>
      );
    }

    if (team.analysisStatus === 'ERROR') {
      return (
        <Badge variant="destructive" className="gap-1.5">
          Error
        </Badge>
      );
    }

    if (team.analysisStatus === 'CANCELLED') {
      return (
        <Badge variant="outline" className="gap-1.5 text-muted-foreground">
          Cancelled
        </Badge>
      );
    }

    if (team.analysisStatus === 'GIT_DONE' || team.analysisStatus === 'AI_ANALYZING') {
      return (
        <Badge variant="outline" className="gap-1.5 text-blue-500 border-blue-500/50 bg-blue-500/10">
          {team.analysisStatus === 'AI_ANALYZING' ? 'AI Analysis' : 'Git Done'}
        </Badge>
      );
    }

    return (
      <Badge variant="outline" className="gap-1.5 text-muted-foreground border-amber-500/50 bg-amber-500/10">
        {team.analysisStatus === 'GIT_ANALYZING' ? 'Git Analysis' : 'Pending'}
      </Badge>
    );
  };

  // Get priority for analysis status (lower = shown first)
  // Failed teams (isFailed) get a separate priority so they sort below teams with real CQI scores
  const getStatusPriority = (team: TeamDTO): number => {
    if (team.isFailed) return 1; // Failed teams after successful DONE teams
    switch (team.analysisStatus) {
      case 'DONE':
        return 0; // Fully completed - show first
      case 'AI_ANALYZING':
        return 2; // AI analysis in progress
      case 'GIT_DONE':
        return 3; // Git analysis done, waiting for AI
      case 'GIT_ANALYZING':
        return 4; // Currently analyzing
      case 'PENDING':
      case 'DOWNLOADING':
        return 5; // Waiting to be analyzed
      case 'ERROR':
        return 6; // Failed
      case 'CANCELLED':
        return 7; // Cancelled
      default:
        return 8; // Unknown
    }
  };

  const sortedAndFilteredTeams = useMemo(() => {
    let filtered = teams.slice();

    // Apply text search filter
    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase();
      filtered = filtered.filter(team => {
        if (team.teamName?.toLowerCase().includes(q)) return true;
        if (team.tutor?.toLowerCase().includes(q)) return true;
        if (team.students?.some(s => s.name?.toLowerCase().includes(q))) return true;
        return false;
      });
    }

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

    // First, sort by analysis status priority (DONE first, then in-progress, then pending)
    filtered.sort((a, b) => {
      const aPriority = getStatusPriority(a);
      const bPriority = getStatusPriority(b);
      if (aPriority !== bPriority) {
        return aPriority - bPriority;
      }
      // If same priority, maintain original order or apply column sort
      return 0;
    });

    // Then apply column sorting within each status group
    if (sortColumn) {
      // Create a stable sort that preserves status priority
      const statusGroups = new Map<number, TeamDTO[]>();
      filtered.forEach(team => {
        const priority = getStatusPriority(team);
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
            comparison = (a.teamName ?? '').localeCompare(b.teamName ?? '');
          } else if (sortColumn === 'commitCount') {
            const aCommits = computeBasicMetrics(a.students).totalCommits;
            const bCommits = computeBasicMetrics(b.students).totalCommits;
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
          filtered = filtered.concat(group);
        }
      });
    }

    return filtered;
  }, [teams, searchQuery, sortColumn, sortDirection, statusFilter]);

  const renderStartDropdown = (label: string, isPending: boolean, onAction: (mode: AnalysisMode) => void) => (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button disabled={isPending || isClearing}>
          {isPending ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
          {isPending ? 'Starting...' : label}
          <ChevronDown className="h-3 w-3 ml-1" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-auto min-w-64">
        <DropdownMenuItem onClick={() => handleStartWithMode('FULL', onAction)} className="flex flex-col items-start gap-0.5">
          <span>Full Analysis</span>
          <span className="text-xs text-muted-foreground font-normal">Git + AI analysis with comprehensive CQI</span>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => handleStartWithMode('SIMPLE', onAction)} className="flex flex-col items-start gap-0.5">
          <span>Simple Analysis</span>
          <span className="text-xs text-muted-foreground font-normal">Git analysis only, no LLM calls (faster)</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );

  const handleStartWithMode = (mode: AnalysisMode, onAction: (mode: AnalysisMode) => void) => {
    if (pairProgrammingEnabled && !hasUploadedAttendanceDocument) {
      setPendingStartMode(mode);
      setPendingStartAction(() => onAction);
      setStartWithoutAttendanceDialogOpen(true);
      return;
    }
    onAction(mode);
  };

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
        return renderStartDropdown('Start Analysis', isStarting, onStart);
      case 'RUNNING':
        return (
          <Button variant="destructive" onClick={() => setCancelDialogOpen(true)} disabled={isCancelling}>
            {isCancelling ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Square className="h-4 w-4" />}
            {isCancelling ? 'Cancelling...' : 'Cancel'}
          </Button>
        );
      case 'DONE':
      case 'ERROR':
        return renderStartDropdown('Force Recompute', isRecomputing, onRecompute);
    }
  };

  const handleClearClick = (type: 'db' | 'files' | 'both', withMappings: boolean) => {
    setClearType(type);
    setClearMappings(withMappings);
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

  const hasValidPairProgrammingData = hasValidPairProgrammingAttendanceData(
    pairProgrammingEnabled,
    uploadedAttendanceFileName,
    pairProgrammingAttendanceByTeamName,
  );

  return (
    <div className="space-y-6 px-4 py-8 max-w-7xl mx-auto">
      <Button variant="outline" onClick={onBackToHome} className="mb-4">
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
          <ExportButton exerciseId={exercise} disabled={teams.length === 0} />
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" disabled={analysisStatus.state === 'RUNNING' || isClearing || isStarting || isRecomputing}>
                {isClearing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                {isClearing ? 'Clearing...' : 'Clear Data'}
                <ChevronDown className="h-3 w-3 ml-1" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-auto min-w-64">
              <DropdownMenuItem onClick={() => handleClearClick('both', false)} className="flex flex-col items-start gap-0.5">
                <span>Clear Analysis</span>
                <span className="text-xs text-muted-foreground font-normal">Removes results and files. Keeps email mappings.</span>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onClick={() => handleClearClick('both', true)}
                className="flex flex-col items-start gap-0.5 text-destructive"
              >
                <span>Clear All</span>
                <span className="text-xs font-normal text-destructive/70">Removes everything including email mappings.</span>
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {pairProgrammingEnabled && (
        <Card className="p-6 shadow-card">
          <div className="flex items-start justify-between gap-4 flex-wrap mb-4">
            <div className="space-y-1">
              <h3 className="text-lg font-semibold">Pair Programming</h3>
              <p className="text-sm text-muted-foreground">
                Upload an XLSX attendance document at any time. Pair programming metrics are calculated independently from AI analysis.
              </p>
            </div>
            <Button variant="outline" onClick={onAttendanceUpload} disabled={!attendanceFile || isAttendanceUploading}>
              {isAttendanceUploading ? <RefreshCw className="h-4 w-4 animate-spin" /> : null}
              {isAttendanceUploading ? 'Analyzing...' : 'Upload Document'}
            </Button>
          </div>

          <div className="space-y-3">
            <FileUpload
              file={attendanceFile}
              onFileSelect={onAttendanceFileSelect}
              disabled={isAttendanceUploading}
              inputId="pair-programming-attendance-file"
            />
            <div className="flex items-center justify-between gap-3 flex-wrap">
              <p className="text-sm text-muted-foreground">
                Used file:{' '}
                <span className="font-medium text-foreground break-all">
                  {isAttendanceUploading ? 'Currently processing...' : (uploadedAttendanceFileName ?? 'Not uploaded yet')}
                </span>
              </p>
              {hasUploadedAttendanceDocument && (
                <Button
                  variant="outline"
                  onClick={() => setRemoveAttendanceDialogOpen(true)}
                  disabled={isAttendanceUploading || isAttendanceClearing}
                >
                  <Trash2 className="h-4 w-4" />
                  Remove File
                </Button>
              )}
            </div>
          </div>
        </Card>
      )}

      <Card className="p-6 shadow-card">
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <div className="flex items-center gap-2 text-sm text-muted-foreground flex-wrap">
            <GitBranch className="h-4 w-4" />
            {templateAuthors.length > 0 ? (
              <>
                <span>Template author{templateAuthors.length > 1 ? 's' : ''}:</span>
                {templateAuthors.map(ta => (
                  <span key={ta.email} className="inline-flex items-center gap-1">
                    <span className="font-medium text-foreground">{ta.email}</span>
                    {ta.autoDetected && (
                      <Badge variant="outline" className="text-xs py-0 h-5">
                        auto-detected
                      </Badge>
                    )}
                  </span>
                ))}
              </>
            ) : (
              <span>No template author configured</span>
            )}
          </div>
          <Button variant="outline" onClick={openTemplateAuthorDialog}>
            <Pencil className="h-4 w-4" />
            {templateAuthors.length > 0 ? 'Edit' : 'Set'}
          </Button>
        </div>
      </Card>

      <ActivityLog status={analysisStatus} />

      <ConfirmationDialog
        open={cancelDialogOpen}
        onOpenChange={setCancelDialogOpen}
        title="Cancel Analysis?"
        description="Are you sure you want to cancel the analysis? This is a time-consuming task and any unprocessed teams will not be analyzed."
        confirmLabel="Yes, Cancel"
        variant="destructive"
        onConfirm={onCancel}
      />

      <ConfirmationDialog
        open={clearDialogOpen}
        onOpenChange={setClearDialogOpen}
        title={clearMappings ? 'Clear All Data' : 'Clear Analysis'}
        description={
          clearMappings
            ? 'This will permanently delete analysis data, repository files, and all email mappings. This action cannot be undone.'
            : 'This will permanently delete analysis data and repository files. Email mappings will be preserved and applied to the next analysis.'
        }
        confirmLabel={clearMappings ? 'Clear All' : 'Clear Analysis'}
        variant="destructive"
        onConfirm={() => onClear(clearType, clearMappings)}
      />

      <ConfirmationDialog
        open={startWithoutAttendanceDialogOpen}
        onOpenChange={setStartWithoutAttendanceDialogOpen}
        title="Start without pair programming document?"
        description="Pair programming is enabled, but no attendance document has been uploaded. You can continue now and upload it later."
        confirmLabel="Start Anyway"
        onConfirm={() => pendingStartAction?.(pendingStartMode)}
      />

      <ConfirmationDialog
        open={removeAttendanceDialogOpen}
        onOpenChange={setRemoveAttendanceDialogOpen}
        title="Remove attendance file?"
        description="Are you sure you want to remove this file? This will clear the pair programming data."
        confirmLabel="Remove File"
        variant="destructive"
        onConfirm={onRemoveUploadedAttendanceFile}
      />

      <AlertDialog open={templateAuthorDialogOpen} onOpenChange={setTemplateAuthorDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Template Authors</AlertDialogTitle>
            <AlertDialogDescription>
              All commits from these email addresses will be excluded from the CQI calculation.
            </AlertDialogDescription>
          </AlertDialogHeader>
          {templateAuthors.length > 0 && (
            <p className="text-sm text-amber-600 bg-amber-50 dark:bg-amber-950/20 border border-amber-200 dark:border-amber-800 rounded-md px-3 py-2">
              Commits from previous template authors were not analyzed and cannot be included retroactively. Re-run the analysis for a full
              recalculation.
            </p>
          )}
          {templateAuthorEmails.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {templateAuthorEmails.map(email => (
                <Badge key={email} variant="secondary" className="gap-1 pr-1">
                  {email}
                  <button
                    onClick={() => removeEmailFromList(email)}
                    className="ml-1 rounded-full p-0.5 hover:bg-muted-foreground/20"
                  >
                    <X className="h-3 w-3" />
                  </button>
                </Badge>
              ))}
            </div>
          )}
          <div className="flex gap-2">
            <Input
              value={newEmailInput}
              onChange={e => setNewEmailInput(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  addEmailToList();
                }
              }}
              placeholder="e.g. template-bot@university.edu"
              className="flex-1"
            />
            <Button variant="outline" onClick={addEmailToList} disabled={!newEmailInput.trim()}>
              Add
            </Button>
          </div>
          {templateAuthorCandidates && templateAuthorCandidates.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {templateAuthorCandidates
                .filter(email => !templateAuthorEmails.includes(email.toLowerCase()))
                .map(email => (
                  <Badge
                    key={email}
                    variant="outline"
                    className="cursor-pointer hover:bg-accent"
                    onClick={() => {
                      const lower = email.toLowerCase();
                      if (!templateAuthorEmails.includes(lower)) {
                        setTemplateAuthorEmails(prev => prev.concat(lower));
                      }
                    }}
                  >
                    {email}
                  </Badge>
                ))}
            </div>
          )}
          <AlertDialogFooter>
            {templateAuthors.length > 0 && (
              <AlertDialogAction
                className="bg-destructive text-destructive-foreground hover:bg-destructive/90 mr-auto"
                onClick={() => onTemplateAuthorsRemove()}
              >
                Remove All
              </AlertDialogAction>
            )}
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={templateAuthorEmails.length === 0}
              onClick={() => onTemplateAuthorsSet(templateAuthorEmails)}
            >
              Save
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

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

      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder="Search teams, students, tutors..."
            className="pl-9 pr-9 bg-white"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
        {searchQuery.trim() && (
          <span className="text-sm text-muted-foreground whitespace-nowrap">
            Showing {sortedAndFilteredTeams.length} of {teams.length} teams
          </span>
        )}
      </div>

      <Card className="overflow-hidden shadow-card">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-primary/10 border-b">
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
                {pairProgrammingEnabled && <th className="text-center py-4 px-6 font-semibold text-sm">Pair Programming</th>}
                <th className="text-left py-4 px-6 font-semibold text-sm">
                  <StatusFilterButton statusFilter={statusFilter} setStatusFilter={setStatusFilter} />
                </th>
              </tr>
            </thead>
            <tbody>
              {sortedAndFilteredTeams.map(team => {
                const pairProgrammingBadgeStatus = getPairProgrammingBadgeStatus(
                  team.teamName ?? '',
                  hasValidPairProgrammingData,
                  pairProgrammingAttendanceByTeamName,
                );

                return (
                  <tr
                    key={String(team.teamId)}
                    onClick={() => onTeamSelect(team, pairProgrammingBadgeStatus)}
                    className="border-b last:border-b-0 hover:bg-muted/30 cursor-pointer transition-colors"
                  >
                    <td className="py-4 px-6">
                      <p className="font-semibold">{(team.teamName ?? '').replace('Team ', '')}</p>
                    </td>
                    <td className="py-4 px-6">
                      <div className="space-y-1">
                        {(team.students ?? []).map((student, idx) => (
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
                      {team.analysisStatus === 'DONE' || team.analysisStatus === 'GIT_DONE' || team.analysisStatus === 'AI_ANALYZING' ? (
                        (() => {
                          const metrics = computeBasicMetrics(team.students);
                          return (
                            <div className="space-y-1">
                              <p className="font-medium">{metrics.totalCommits}</p>
                              <p className="text-xs text-muted-foreground">{metrics.totalLines} lines</p>
                            </div>
                          );
                        })()
                      ) : team.analysisStatus === 'PENDING' ||
                        team.analysisStatus === 'DOWNLOADING' ||
                        team.analysisStatus === 'GIT_ANALYZING' ? (
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
                      {team.isFailed ? (
                        <div className="flex items-center gap-2 text-destructive">
                          <span className="text-sm">Failed</span>
                        </div>
                      ) : team.analysisStatus === 'DONE' && team.cqi !== undefined ? (
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
                          <span className="text-sm">{team.analysisStatus === 'GIT_ANALYZING' ? 'Git Analysis...' : 'Pending'}</span>
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
                    {pairProgrammingEnabled && (
                      <td className="py-4 px-6">
                        <div className="flex flex-wrap items-center gap-2">
                          <PairProgrammingBadge status={pairProgrammingBadgeStatus} />
                        </div>
                      </td>
                    )}
                    <td className="py-4 px-6">
                      <div className="flex flex-wrap items-center gap-2">
                        {renderAnalysisStatusBadge(team)}
                        {team.orphanCommitCount != null && team.orphanCommitCount > 0 && (
                          <Badge className="bg-amber-100 text-amber-700 border-amber-200">{team.orphanCommitCount} unmatched</Badge>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};

export default TeamsList;

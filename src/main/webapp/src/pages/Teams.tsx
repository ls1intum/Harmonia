import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, useMemo, useEffect, useRef } from 'react';
import TeamsList from '@/components/TeamsList';
import { computeCourseAverages } from '@/lib/courseAverages';
import { toast } from '@/hooks/use-toast';
import { useAnalysisStatus, cancelAnalysis, clearData } from '@/hooks/useAnalysisStatus';
import { loadBasicTeamDataStream, transformSummaryToTeamDTO, type TemplateAuthorInfo, type TeamDTO } from '@/data/dataLoaders';
import type { AnalysisMode } from '@/hooks/useAnalysisStatus';
import { getPairProgrammingAttendanceFileStorageKey } from '@/lib/pairProgramming';
import { pairProgrammingApi, emailMappingApi, requestApi } from '@/lib/apiClient';

/**
 * Teams page — the main analysis dashboard.
 *
 * Fetches teams for the selected exercise, manages analysis lifecycle
 * (start / cancel / recompute / clear), pair-programming attendance
 * upload, and template-author assignment.
 */
export default function Teams() {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const {
    course,
    exercise,
    pairProgrammingEnabled = true,
  } = (location.state || {}) as {
    course: string;
    exercise: string;
    pairProgrammingEnabled?: boolean;
  };
  const attendanceStorageKey = getPairProgrammingAttendanceFileStorageKey(exercise);
  const [attendanceFile, setAttendanceFile] = useState<File | null>(null);
  const [uploadedAttendanceFileName, setUploadedAttendanceFileName] = useState<string | null>(() =>
    window.sessionStorage.getItem(attendanceStorageKey),
  );
  // Template author candidates — no server endpoint, managed purely via query cache
  const templateAuthorCandidates = queryClient.getQueryData<string[] | null>(['templateAuthorCandidates', exercise]) ?? null;

  // --- Queries ---

  const { data: templateAuthor = null } = useQuery<TemplateAuthorInfo | null>({
    queryKey: ['templateAuthor', exercise],
    queryFn: async () => {
      const response = await emailMappingApi.getTemplateAuthor(parseInt(exercise));
      const data = response.data;
      if (!data) return null;
      return { email: data.templateEmail ?? '', autoDetected: data.autoDetected ?? false };
    },
    enabled: !!exercise,
    staleTime: 60 * 1000,
  });

  // Use new server-synced status hook
  const {
    status,
    refetch: refetchStatus,
    isLoading: isStatusLoading,
  } = useAnalysisStatus({
    exerciseId: exercise,
    enabled: !!exercise,
  });

  // Pair programming scores recomputing status
  const { data: pairProgrammingRecomputing } = useQuery({
    queryKey: ['pairProgrammingRecomputing', exercise],
    queryFn: () => pairProgrammingApi.isRecomputing(parseInt(exercise)).then(res => res.data),
    enabled: !!exercise && pairProgrammingEnabled,
    refetchInterval: query => (query.state.data?.recomputing ? 2000 : false),
    staleTime: 0,
  });

  const isPairProgrammingScoresUpdating = pairProgrammingRecomputing?.recomputing ?? false;

  // When PP recompute finishes, refetch teams to get updated server PP status
  const prevRecomputing = useRef(false);
  useEffect(() => {
    const isRecomputing = pairProgrammingRecomputing?.recomputing ?? false;
    if (prevRecomputing.current && !isRecomputing) {
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
    }
    prevRecomputing.current = isRecomputing;
  }, [pairProgrammingRecomputing?.recomputing, queryClient, exercise]);

  // Fetch team summaries from database on load (one-time, no polling).
  // During analysis, SSE is the single source of truth for updates.
  const isAnalysisRunning = status.state === 'RUNNING';
  const { data: teams = [] } = useQuery<TeamDTO[]>({
    queryKey: ['teams', exercise],
    queryFn: async () => {
      const response = await requestApi.getTeamSummaries(parseInt(exercise));
      return response.data.map(transformSummaryToTeamDTO);
    },
    staleTime: 30 * 1000,
    gcTime: 10 * 60 * 1000,
    enabled: !!exercise,
    refetchOnWindowFocus: !isAnalysisRunning,
    // On error, keep showing cached/SSE-accumulated data instead of clearing
    retry: 1,
  });

  const toggleReviewedMutation = useMutation({
    mutationFn: async (teamId: string) => {
      const response = await requestApi.toggleReviewStatus(parseInt(exercise), parseInt(teamId));
      return response.data;
    },
    onMutate: async teamId => {
      await queryClient.cancelQueries({ queryKey: ['teams', exercise] });
      const previous = queryClient.getQueryData<TeamDTO[]>(['teams', exercise]);
      queryClient.setQueryData(['teams', exercise], (old: TeamDTO[] = []) =>
        old.map(t => (String(t.teamId) === teamId ? Object.assign({}, t, { isReviewed: !t.isReviewed }) : t)),
      );
      return { previous };
    },
    onError: (_err, _teamId, context) => {
      if (context?.previous) {
        queryClient.setQueryData(['teams', exercise], context.previous);
      }
      toast({ variant: 'destructive', title: 'Failed to toggle review status' });
    },
    onSuccess: () => {},
  });

  // --- Mutations ---

  const attendanceUploadMutation = useMutation({
    mutationFn: async (file: File) => {
      const courseId = Number.parseInt(course ?? '', 10);
      const exerciseId = Number.parseInt(exercise ?? '', 10);
      if (Number.isNaN(courseId) || Number.isNaN(exerciseId)) {
        throw new Error('Invalid course or exercise ID');
      }
      return pairProgrammingApi.uploadAttendance(courseId, exerciseId, file);
    },
    onSuccess: (_response, file) => {
      setUploadedAttendanceFileName(file.name);
      window.sessionStorage.setItem(attendanceStorageKey, file.name);
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      queryClient.invalidateQueries({ queryKey: ['pairProgrammingRecomputing', exercise] });
      toast({
        title: 'Attendance uploaded',
        description: 'Pair programming metrics are being updated.',
      });
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'Attendance upload failed',
        description: 'Could not process the attendance file.',
      });
    },
  });

  const clearAttendanceMutation = useMutation({
    mutationFn: async () => {
      const exerciseId = Number.parseInt(exercise ?? '', 10);
      if (Number.isNaN(exerciseId)) {
        throw new Error('Invalid exercise ID');
      }

      await pairProgrammingApi.clearAttendance(exerciseId);
    },
    onSuccess: () => {
      setAttendanceFile(null);
      setUploadedAttendanceFileName(null);
      window.sessionStorage.removeItem(attendanceStorageKey);
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      toast({
        title: 'Attendance file removed',
        description: 'Pair programming data was cleared.',
      });
    },
    onError: error => {
      toast({
        variant: 'destructive',
        title: 'Failed to remove attendance file',
        description: error instanceof Error ? error.message : 'Could not clear pair programming data.',
      });
    },
  });

  // Mutation for starting analysis (accepts mode from the UI)
  const startMutation = useMutation({
    mutationFn: async (mode: AnalysisMode) => {
      // Step 1: Immediately update UI - clear teams cache and set status to RUNNING
      // This ensures the button changes immediately to "Cancel"
      queryClient.setQueryData(['teams', exercise], []);
      queryClient.setQueryData(['analysisStatus', exercise], {
        state: 'RUNNING' as const,
        totalTeams: 0,
        processedTeams: 0,
        currentTeamName: undefined,
        currentStage: undefined,
        analysisMode: mode,
      });

      toast({ title: 'Starting analysis...' });

      // Step 2: Start streaming (server will clear data before starting)
      queryClient.setQueryData(['templateAuthor', exercise], null);
      queryClient.setQueryData(['templateAuthorCandidates', exercise], null);
      return new Promise<void>((resolve, reject) => {
        loadBasicTeamDataStream(
          exercise,
          () => {}, // onTotal
          // onInit: Add team with pending status
          team => {
            queryClient.setQueryData(['teams', exercise], (old: TeamDTO[] = []) => {
              const exists = old.some(t => t.teamId === team.teamId);
              if (exists) return old;
              return old.concat(team);
            });
          },
          // onUpdate: Update existing team with new data
          update => {
            queryClient.setQueryData(['teams', exercise], (old: TeamDTO[] = []) => {
              const existing = old.find(t => t.teamId === update.teamId);
              if (existing) {
                return old.map(t => (t.teamId === update.teamId ? Object.assign({}, t, update) : t));
              }
              return old.concat(update as TeamDTO);
            });
          },
          () => {
            refetchStatus();
            resolve();
          },
          error => {
            reject(error);
          },
          undefined, // onPhaseChange
          undefined, // onGitDone — PP statuses arrive via GIT_UPDATE events; DB is updated by synchronous recompute for page refreshes
          info => queryClient.setQueryData(['templateAuthor', exercise], info),
          candidates => queryClient.setQueryData(['templateAuthorCandidates', exercise], candidates),
          mode,
          statusUpdate => {
            // Don't override CANCELLED status with RUNNING from lingering SSE events
            const currentStatus = queryClient.getQueryData<typeof status>(['analysisStatus', exercise]);
            if (currentStatus?.state === 'CANCELLED') return;
            queryClient.setQueryData(['analysisStatus', exercise], (old: typeof status) =>
              Object.assign({}, old, {
                state: 'RUNNING' as const,
                analysisMode: mode,
                processedTeams: statusUpdate.processedTeams,
                totalTeams: statusUpdate.totalTeams,
                currentTeamName: statusUpdate.currentTeamName,
                currentStage: statusUpdate.currentStage,
              }),
            );
          },
        );
      });
    },
    onSuccess: () => {
      // Check if analysis was cancelled — the SSE CANCELLED event also resolves via onComplete
      const currentStatus = queryClient.getQueryData<typeof status>(['analysisStatus', exercise]);
      if (currentStatus?.state === 'CANCELLED') {
        // Already handled by cancelMutation.onSuccess — just refetch to be safe
        queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
        refetchStatus();
        return;
      }
      toast({ title: 'Analysis completed!' });
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      queryClient.invalidateQueries({ queryKey: ['templateAuthor', exercise] });
      refetchStatus();
    },
    onError: (error: Error) => {
      if (error?.message === 'ALREADY_RUNNING') {
        // Analysis was already running - this is not an error, just inform the user
        toast({ title: 'Analysis already in progress', description: 'Showing current progress...' });
        refetchStatus();
        return;
      }
      // Don't show error toast if analysis was cancelled
      const currentStatus = queryClient.getQueryData<typeof status>(['analysisStatus', exercise]);
      if (currentStatus?.state === 'CANCELLED') {
        queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
        refetchStatus();
        return;
      }
      toast({
        variant: 'destructive',
        title: 'Failed to start analysis',
      });
      refetchStatus();
    },
  });

  // Mutation for cancelling
  const cancelMutation = useMutation({
    mutationFn: async () => {
      // Optimistically update status to CANCELLED immediately
      queryClient.setQueryData(['analysisStatus', exercise], (old: typeof status) =>
        Object.assign({}, old, { state: 'CANCELLED' as const }),
      );
      toast({ title: 'Cancelling analysis...' });
      return cancelAnalysis(exercise);
    },
    onSuccess: () => {
      toast({ title: 'Analysis cancelled' });
      // Don't invalidate teams here — the server may still be processing
      // (DB was cleared at analysis start, teams may not be re-initialized yet).
      // The SSE stream will close shortly and startMutation/recomputeMutation
      // handlers will refetch teams once the server is in a consistent state.
      refetchStatus();
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'Failed to cancel analysis' });
      refetchStatus();
    },
  });

  // Mutation for recompute (force) - same as start since server clears data first
  const recomputeMutation = useMutation({
    mutationFn: async (mode: AnalysisMode) => {
      // Step 1: Immediately update UI - clear teams cache and set status to RUNNING
      queryClient.setQueryData(['teams', exercise], []);
      queryClient.setQueryData(['analysisStatus', exercise], {
        state: 'RUNNING' as const,
        totalTeams: 0,
        processedTeams: 0,
        currentTeamName: undefined,
        currentStage: undefined,
        analysisMode: mode,
      });

      toast({ title: 'Forcing reanalysis...' });

      // Step 2: Start streaming (server will clear data before starting)
      queryClient.setQueryData(['templateAuthor', exercise], null);
      queryClient.setQueryData(['templateAuthorCandidates', exercise], null);
      return new Promise<void>((resolve, reject) => {
        loadBasicTeamDataStream(
          exercise,
          () => {},
          team => {
            queryClient.setQueryData(['teams', exercise], (old: TeamDTO[] = []) => {
              const exists = old.some(t => t.teamId === team.teamId);
              if (exists) return old;
              return old.concat(team);
            });
          },
          update => {
            queryClient.setQueryData(['teams', exercise], (old: TeamDTO[] = []) => {
              const existing = old.find(t => t.teamId === update.teamId);
              if (existing) {
                return old.map(t => (t.teamId === update.teamId ? Object.assign({}, t, update) : t));
              }
              return old.concat(update as TeamDTO);
            });
          },
          () => {
            refetchStatus();
            resolve();
          },
          error => {
            reject(error);
          },
          undefined, // onPhaseChange
          () => {
            queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
          }, // onGitDone
          info => queryClient.setQueryData(['templateAuthor', exercise], info),
          candidates => queryClient.setQueryData(['templateAuthorCandidates', exercise], candidates),
          mode,
          statusUpdate => {
            const currentStatus = queryClient.getQueryData<typeof status>(['analysisStatus', exercise]);
            if (currentStatus?.state === 'CANCELLED') return;
            queryClient.setQueryData(['analysisStatus', exercise], (old: typeof status) =>
              Object.assign({}, old, {
                state: 'RUNNING' as const,
                analysisMode: mode,
                processedTeams: statusUpdate.processedTeams,
                totalTeams: statusUpdate.totalTeams,
                currentTeamName: statusUpdate.currentTeamName,
                currentStage: statusUpdate.currentStage,
              }),
            );
          },
        );
      });
    },
    onSuccess: () => {
      const currentStatus = queryClient.getQueryData<typeof status>(['analysisStatus', exercise]);
      if (currentStatus?.state === 'CANCELLED') {
        queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
        refetchStatus();
        return;
      }
      toast({ title: 'Reanalysis completed!' });
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      queryClient.invalidateQueries({ queryKey: ['templateAuthor', exercise] });
      refetchStatus();
    },
    onError: (error: Error) => {
      if (error?.message === 'ALREADY_RUNNING') {
        toast({ title: 'Analysis already in progress', description: 'Showing current progress...' });
        refetchStatus();
        return;
      }
      const currentStatus = queryClient.getQueryData<typeof status>(['analysisStatus', exercise]);
      if (currentStatus?.state === 'CANCELLED') {
        queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
        refetchStatus();
        return;
      }
      toast({
        variant: 'destructive',
        title: 'Failed to reanalyze',
      });
      refetchStatus();
    },
  });

  // Mutation for clear
  const clearMutation = useMutation({
    mutationFn: ({ type, clearMappings }: { type: 'db' | 'files' | 'both'; clearMappings?: boolean }) =>
      clearData(exercise, type, clearMappings),
    onSuccess: () => {
      setAttendanceFile(null);
      setUploadedAttendanceFileName(null);
      window.sessionStorage.removeItem(attendanceStorageKey);
      toast({ title: 'Data cleared successfully' });
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      refetchStatus();
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'Failed to clear data',
      });
    },
  });

  const courseAverages = useMemo(() => computeCourseAverages(teams), [teams]);

  const setTemplateAuthorMutation = useMutation({
    mutationFn: async (email: string) => {
      await emailMappingApi.setTemplateAuthor(parseInt(exercise), { templateEmail: email });
      return email;
    },
    onSuccess: email => {
      queryClient.setQueryData(['templateAuthor', exercise], { email, autoDetected: false });
      queryClient.setQueryData(['templateAuthorCandidates', exercise], null);
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      toast({ title: 'Template author set', description: email });
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'Failed to set template author' });
    },
  });

  const removeTemplateAuthorMutation = useMutation({
    mutationFn: async () => {
      await emailMappingApi.deleteTemplateAuthor(parseInt(exercise));
    },
    onSuccess: () => {
      queryClient.setQueryData(['templateAuthor', exercise], null);
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      toast({ title: 'Template author removed' });
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'Failed to remove template author' });
    },
  });

  // Redirect if no course/exercise
  if (!course || !exercise) {
    navigate('/');
    return null;
  }

  // --- Handlers ---

  const handleTeamSelect = (team: TeamDTO) => {
    navigate(`/teams/${String(team.teamId)}`, {
      state: {
        teamId: team.teamId,
        course,
        exercise,
        pairProgrammingEnabled,
        courseAverages,
        analysisMode: status.analysisMode,
        teamsSearchParams: searchParams.toString(),
      },
    });
  };

  const handleAttendanceUpload = () => {
    if (!attendanceFile) {
      toast({
        variant: 'destructive',
        title: 'No file selected',
        description: 'Please select an XLSX file before uploading.',
      });
      return;
    }
    attendanceUploadMutation.mutate(attendanceFile);
  };

  const handleRemoveUploadedAttendanceFile = () => {
    clearAttendanceMutation.mutate();
  };

  const handleTemplateAuthorSet = (email: string) => {
    setTemplateAuthorMutation.mutate(email);
  };

  const handleTemplateAuthorRemove = () => {
    removeTemplateAuthorMutation.mutate();
  };

  // --- Render ---

  return (
    <TeamsList
      teams={teams}
      courseAverages={courseAverages}
      onTeamSelect={handleTeamSelect}
      onToggleReviewed={teamId => toggleReviewedMutation.mutate(teamId)}
      onBackToHome={() => navigate('/')}
      onStart={(mode: AnalysisMode) => startMutation.mutate(mode)}
      onCancel={() => cancelMutation.mutate()}
      onRecompute={(mode: AnalysisMode) => recomputeMutation.mutate(mode)}
      onClear={(type, clearMappings) => clearMutation.mutate({ type, clearMappings })}
      course={course}
      exercise={exercise}
      analysisStatus={status}
      pairProgrammingEnabled={pairProgrammingEnabled}
      attendanceFile={attendanceFile}
      uploadedAttendanceFileName={uploadedAttendanceFileName}
      onAttendanceFileSelect={setAttendanceFile}
      onAttendanceUpload={handleAttendanceUpload}
      onRemoveUploadedAttendanceFile={handleRemoveUploadedAttendanceFile}
      templateAuthor={templateAuthor}
      templateAuthorCandidates={templateAuthorCandidates}
      onTemplateAuthorSet={handleTemplateAuthorSet}
      onTemplateAuthorRemove={handleTemplateAuthorRemove}
      isLoading={isStatusLoading}
      isStarting={startMutation.isPending}
      isCancelling={cancelMutation.isPending}
      isRecomputing={recomputeMutation.isPending}
      isClearing={clearMutation.isPending}
      isAttendanceUploading={attendanceUploadMutation.isPending}
      isAttendanceClearing={clearAttendanceMutation.isPending}
      isPairProgrammingScoresUpdating={isPairProgrammingScoresUpdating}
    />
  );
}

import { useLocation, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, useMemo } from 'react';
import TeamsList from '@/components/TeamsList';
import type { TeamAttendanceDTO, TeamsScheduleDTO } from '@/app/generated';
import { computeCourseAverages } from '@/lib/courseAverages';
import { toast } from '@/hooks/use-toast';
import { useAnalysisStatus, cancelAnalysis, clearData } from '@/hooks/useAnalysisStatus';
import { loadBasicTeamDataStream, transformSummaryToTeamDTO, type TemplateAuthorInfo, type TeamDTO } from '@/data/dataLoaders';
import type { AnalysisMode } from '@/hooks/useAnalysisStatus';
import { normalizeTeamName } from '@/lib/utils';
import {
  getPairProgrammingAttendanceFileStorageKey,
  getPairProgrammingAttendanceMapStorageKey,
  readStoredPairProgrammingAttendanceMap,
  type PairProgrammingAttendanceMap,
  type PairProgrammingBadgeStatus,
} from '@/lib/pairProgramming';
import { pairProgrammingApi, emailMappingApi, requestApi } from '@/lib/apiClient';

type NullableAttendanceMap = Record<string, boolean | null>;

const hasCancelledSessionAttendance = (attendance?: TeamAttendanceDTO): boolean => {
  const student1Attendance = (attendance?.student1Attendance ?? {}) as NullableAttendanceMap;
  const student2Attendance = (attendance?.student2Attendance ?? {}) as NullableAttendanceMap;
  return Object.values(student1Attendance)
    .concat(Object.values(student2Attendance))
    .some(value => value === null);
};

const buildPairProgrammingAttendanceMap = (schedule?: TeamsScheduleDTO): PairProgrammingAttendanceMap => {
  const teams = schedule?.teams;
  if (!teams) {
    return {};
  }

  return Object.entries(teams).reduce<PairProgrammingAttendanceMap>((acc, [teamName, attendance]) => {
    if (!teamName) {
      return acc;
    }
    const hasCancelledSessionWarning = hasCancelledSessionAttendance(attendance);
    acc[normalizeTeamName(teamName)] = hasCancelledSessionWarning
      ? 'warning'
      : attendance?.pairedMandatorySessions === true
        ? 'pass'
        : 'fail';
    return acc;
  }, {});
};

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
  const pairProgrammingAttendanceMapStorageKey = getPairProgrammingAttendanceMapStorageKey(exercise);
  const [attendanceFile, setAttendanceFile] = useState<File | null>(null);
  const [uploadedAttendanceFileName, setUploadedAttendanceFileName] = useState<string | null>(() =>
    window.sessionStorage.getItem(attendanceStorageKey),
  );
  const [pairProgrammingAttendanceByTeamName, setPairProgrammingAttendanceByTeamName] = useState<PairProgrammingAttendanceMap>(() =>
    readStoredPairProgrammingAttendanceMap(pairProgrammingAttendanceMapStorageKey),
  );
  // Template author candidates — no server endpoint, managed purely via query cache
  const templateAuthorCandidates = queryClient.getQueryData<string[] | null>(['templateAuthorCandidates', exercise]) ?? null;

  // --- Queries ---

  const { data: templateAuthors = [] } = useQuery<TemplateAuthorInfo[]>({
    queryKey: ['templateAuthors', exercise],
    queryFn: async () => {
      const response = await emailMappingApi.getTemplateAuthor(parseInt(exercise));
      const data = response.data;
      if (!Array.isArray(data)) return [];
      return data.map(d => ({ email: d.templateEmail ?? '', autoDetected: d.autoDetected ?? false }));
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

  // Fetch team summaries from database on load (one-time, no polling).
  // During analysis, SSE is the single source of truth for updates.
  const isAnalysisRunning = status.state === 'RUNNING';
  const { data: teams = [] } = useQuery<TeamDTO[]>({
    queryKey: ['teams', exercise],
    queryFn: async () => {
      try {
        const response = await requestApi.getTeamSummaries(parseInt(exercise));
        return response.data.map(transformSummaryToTeamDTO);
      } catch {
        return [];
      }
    },
    staleTime: 30 * 1000,
    gcTime: 10 * 60 * 1000,
    enabled: !!exercise,
    refetchOnWindowFocus: !isAnalysisRunning,
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
    onSuccess: (response, file) => {
      const pairProgrammingAttendanceMap = buildPairProgrammingAttendanceMap(response.data);
      setUploadedAttendanceFileName(file.name);
      window.sessionStorage.setItem(attendanceStorageKey, file.name);
      setPairProgrammingAttendanceByTeamName(pairProgrammingAttendanceMap);
      window.sessionStorage.setItem(pairProgrammingAttendanceMapStorageKey, JSON.stringify(pairProgrammingAttendanceMap));
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      toast({
        title: 'Attendance uploaded',
        description: 'Pair programming metrics were updated.',
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
      setPairProgrammingAttendanceByTeamName({});
      window.sessionStorage.removeItem(attendanceStorageKey);
      window.sessionStorage.removeItem(pairProgrammingAttendanceMapStorageKey);
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
      queryClient.setQueryData(['templateAuthors', exercise], []);
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
          undefined, // onGitDone
          info =>
            queryClient.setQueryData(['templateAuthors', exercise], (old: TemplateAuthorInfo[] = []) => {
              if (old.some(a => a.email === info.email)) return old;
              return old.concat(info);
            }),
          candidates => queryClient.setQueryData(['templateAuthorCandidates', exercise], candidates),
          mode,
          statusUpdate => {
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
      toast({ title: 'Analysis completed!' });
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      queryClient.invalidateQueries({ queryKey: ['templateAuthors', exercise] });
      refetchStatus();
    },
    onError: (error: Error) => {
      if (error?.message === 'ALREADY_RUNNING') {
        // Analysis was already running - this is not an error, just inform the user
        toast({ title: 'Analysis already in progress', description: 'Showing current progress...' });
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
      // Invalidate to get fresh data including cancelled teams
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
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
      queryClient.setQueryData(['templateAuthors', exercise], []);
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
          undefined, // onGitDone
          info =>
            queryClient.setQueryData(['templateAuthors', exercise], (old: TemplateAuthorInfo[] = []) => {
              if (old.some(a => a.email === info.email)) return old;
              return old.concat(info);
            }),
          candidates => queryClient.setQueryData(['templateAuthorCandidates', exercise], candidates),
          mode,
          statusUpdate => {
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
      toast({ title: 'Reanalysis completed!' });
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      queryClient.invalidateQueries({ queryKey: ['templateAuthors', exercise] });
      refetchStatus();
    },
    onError: (error: Error) => {
      if (error?.message === 'ALREADY_RUNNING') {
        toast({ title: 'Analysis already in progress', description: 'Showing current progress...' });
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
      setPairProgrammingAttendanceByTeamName({});
      window.sessionStorage.removeItem(attendanceStorageKey);
      window.sessionStorage.removeItem(pairProgrammingAttendanceMapStorageKey);
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

  const setTemplateAuthorsMutation = useMutation({
    mutationFn: async (emails: string[]) => {
      await emailMappingApi.setTemplateAuthors(
        parseInt(exercise),
        emails.map(e => ({ templateEmail: e })),
      );
      return emails;
    },
    onSuccess: emails => {
      queryClient.setQueryData(
        ['templateAuthors', exercise],
        emails.map(email => ({ email, autoDetected: false })),
      );
      queryClient.setQueryData(['templateAuthorCandidates', exercise], null);
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      toast({ title: 'Template authors updated', description: emails.join(', ') });
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'Failed to set template authors' });
    },
  });

  const removeTemplateAuthorsMutation = useMutation({
    mutationFn: async () => {
      await emailMappingApi.deleteTemplateAuthor(parseInt(exercise));
    },
    onSuccess: () => {
      queryClient.setQueryData(['templateAuthors', exercise], []);
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
      toast({ title: 'Template authors removed' });
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'Failed to remove template authors' });
    },
  });

  // Redirect if no course/exercise
  if (!course || !exercise) {
    navigate('/');
    return null;
  }

  // --- Handlers ---

  const handleTeamSelect = (team: TeamDTO, pairProgrammingBadgeStatus: PairProgrammingBadgeStatus | null) => {
    navigate(`/teams/${String(team.teamId)}`, {
      state: {
        teamId: team.teamId,
        course,
        exercise,
        pairProgrammingEnabled,
        pairProgrammingBadgeStatus,
        courseAverages,
        analysisMode: status.analysisMode,
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

  const handleTemplateAuthorsSet = (emails: string[]) => {
    setTemplateAuthorsMutation.mutate(emails);
  };

  const handleTemplateAuthorsRemove = () => {
    removeTemplateAuthorsMutation.mutate();
  };

  // --- Render ---

  return (
    <TeamsList
      teams={teams}
      courseAverages={courseAverages}
      onTeamSelect={handleTeamSelect}
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
      pairProgrammingAttendanceByTeamName={pairProgrammingAttendanceByTeamName}
      onAttendanceFileSelect={setAttendanceFile}
      onAttendanceUpload={handleAttendanceUpload}
      onRemoveUploadedAttendanceFile={handleRemoveUploadedAttendanceFile}
      templateAuthors={templateAuthors}
      templateAuthorCandidates={templateAuthorCandidates}
      onTemplateAuthorsSet={handleTemplateAuthorsSet}
      onTemplateAuthorsRemove={handleTemplateAuthorsRemove}
      isLoading={isStatusLoading}
      isStarting={startMutation.isPending}
      isCancelling={cancelMutation.isPending}
      isRecomputing={recomputeMutation.isPending}
      isClearing={clearMutation.isPending}
      isAttendanceUploading={attendanceUploadMutation.isPending}
      isAttendanceClearing={clearAttendanceMutation.isPending}
    />
  );
}

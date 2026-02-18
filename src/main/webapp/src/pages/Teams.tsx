import { useLocation, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import TeamsList from '@/components/TeamsList';
import type { Team } from '@/types/team';
import { toast } from '@/hooks/use-toast';
import { useAnalysisStatus, cancelAnalysis, clearData } from '@/hooks/useAnalysisStatus';
import { loadBasicTeamDataStream, transformToComplexTeamData } from '@/data/dataLoaders';
import { AttendanceResourceApi, Configuration, type TeamAttendanceDTO, type TeamsScheduleDTO } from '@/app/generated';
import { normalizeTeamName } from '@/lib/utils';
import {
  getPairProgrammingAttendanceFileStorageKey,
  getPairProgrammingAttendanceMapStorageKey,
  readStoredPairProgrammingAttendanceMap,
  type PairProgrammingAttendanceMap,
  type PairProgrammingBadgeStatus,
} from '@/lib/pairProgramming';

const apiConfig = new Configuration({
  basePath: window.location.origin,
  baseOptions: {
    withCredentials: true,
  },
});
const attendanceApi = new AttendanceResourceApi(apiConfig);

type NullableAttendanceMap = Record<string, boolean | null>;

const hasCancelledSessionAttendance = (attendance?: TeamAttendanceDTO): boolean => {
  const student1Attendance = (attendance?.student1Attendance ?? {}) as NullableAttendanceMap;
  const student2Attendance = (attendance?.student2Attendance ?? {}) as NullableAttendanceMap;
  return [...Object.values(student1Attendance), ...Object.values(student2Attendance)].some(value => value === null);
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
      : attendance?.pairedAtLeastTwoOfThree === true
        ? 'pass'
        : 'fail';
    return acc;
  }, {});
};

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

  // Use new server-synced status hook
  const {
    status,
    refetch: refetchStatus,
    isLoading: isStatusLoading,
  } = useAnalysisStatus({
    exerciseId: exercise,
    enabled: !!exercise,
  });

  // Fetch teams from database on load
  // During analysis, this shows already-analyzed teams
  const isAnalysisRunning = status.state === 'RUNNING';
  const { data: teams = [] } = useQuery<Team[]>({
    queryKey: ['teams', exercise],
    queryFn: async () => {
      // Fetch already-analyzed teams from database (filtered by exerciseId)
      const response = await fetch(`/api/requestResource/${exercise}/getData`);
      if (!response.ok) return [];
      const data = await response.json();
      // Transform to Team type
      return data.map(transformToComplexTeamData);
    },
    staleTime: isAnalysisRunning ? 2000 : 30 * 1000, // Faster updates during analysis
    gcTime: 10 * 60 * 1000,
    enabled: !!exercise,
    refetchInterval: isAnalysisRunning ? 3000 : false, // Poll every 3s during analysis to get new teams
    refetchOnWindowFocus: !isAnalysisRunning,
  });

  const attendanceUploadMutation = useMutation({
    mutationFn: async (file: File) => {
      const courseId = Number.parseInt(course ?? '', 10);
      const exerciseId = Number.parseInt(exercise ?? '', 10);
      if (Number.isNaN(courseId) || Number.isNaN(exerciseId)) {
        throw new Error('Invalid course or exercise ID');
      }
      return attendanceApi.uploadAttendance(courseId, exerciseId, file);
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

      const response = await fetch(`/api/attendance/clear?exerciseId=${exerciseId}`, {
        method: 'POST',
        credentials: 'include',
      });

      if (!response.ok) {
        const errorMessage = (await response.text()) || response.statusText;
        throw new Error(`Could not clear attendance data (${response.status}): ${errorMessage}`);
      }
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

  // Mutation for starting analysis
  const startMutation = useMutation({
    mutationFn: async () => {
      // Step 1: Immediately update UI - clear teams cache and set status to RUNNING
      // This ensures the button changes immediately to "Cancel"
      queryClient.setQueryData(['teams', exercise], []);
      queryClient.setQueryData(['analysisStatus', exercise], {
        state: 'RUNNING' as const,
        totalTeams: 0,
        processedTeams: 0,
        currentTeamName: undefined,
        currentStage: undefined,
      });

      toast({ title: 'Starting analysis...' });

      // Step 2: Start streaming (server will clear data before starting)
      return new Promise<void>((resolve, reject) => {
        loadBasicTeamDataStream(
          exercise,
          () => {}, // onTotal
          // onInit: Add team with pending status
          team => {
            queryClient.setQueryData(['teams', exercise], (old: Team[] = []) => {
              const exists = old.some(t => t.id === (team as unknown as Team).id);
              if (exists) return old;
              return [...old, team as unknown as Team];
            });
          },
          // onUpdate: Update existing team with new data (merge for partial updates like ANALYZING)
          team => {
            queryClient.setQueryData(['teams', exercise], (old: Team[] = []) => {
              const teamData = team as unknown as Team;
              const existingTeam = old.find(t => t.id === teamData.id);
              if (existingTeam) {
                // Merge: keep existing data, override with new data
                // This handles partial updates (ANALYZING) and full updates (UPDATE)
                return old.map(t => (t.id === teamData.id ? { ...t, ...teamData } : t));
              }
              return [...old, teamData];
            });
          },
          () => {
            refetchStatus();
            resolve();
          },
          error => {
            reject(error);
          },
        );
      });
    },
    onSuccess: () => {
      toast({ title: 'Analysis completed!' });
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
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
      queryClient.setQueryData(['analysisStatus', exercise], (old: typeof status) => ({
        ...old,
        state: 'CANCELLED' as const,
      }));
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
    mutationFn: async () => {
      // Step 1: Immediately update UI - clear teams cache and set status to RUNNING
      queryClient.setQueryData(['teams', exercise], []);
      queryClient.setQueryData(['analysisStatus', exercise], {
        state: 'RUNNING' as const,
        totalTeams: 0,
        processedTeams: 0,
        currentTeamName: undefined,
        currentStage: undefined,
      });

      toast({ title: 'Forcing reanalysis...' });

      // Step 2: Start streaming (server will clear data before starting)
      return new Promise<void>((resolve, reject) => {
        loadBasicTeamDataStream(
          exercise,
          () => {},
          team => {
            queryClient.setQueryData(['teams', exercise], (old: Team[] = []) => {
              const exists = old.some(t => t.id === (team as unknown as Team).id);
              if (exists) return old;
              return [...old, team as unknown as Team];
            });
          },
          team => {
            queryClient.setQueryData(['teams', exercise], (old: Team[] = []) => {
              const teamData = team as unknown as Team;
              const existingTeam = old.find(t => t.id === teamData.id);
              if (existingTeam) {
                // Merge: keep existing data, override with new data
                return old.map(t => (t.id === teamData.id ? { ...t, ...teamData } : t));
              }
              return [...old, teamData];
            });
          },
          () => {
            refetchStatus();
            resolve();
          },
          error => {
            reject(error);
          },
        );
      });
    },
    onSuccess: () => {
      toast({ title: 'Reanalysis completed!' });
      queryClient.invalidateQueries({ queryKey: ['teams', exercise] });
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
    mutationFn: (type: 'db' | 'files' | 'both') => clearData(exercise, type),
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

  // Redirect if no course/exercise
  if (!course || !exercise) {
    navigate('/');
    return null;
  }

  const handleTeamSelect = (team: Team, pairProgrammingBadgeStatus: PairProgrammingBadgeStatus | null) => {
    navigate(`/teams/${team.id}`, { state: { team, course, exercise, pairProgrammingEnabled, pairProgrammingBadgeStatus } });
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

  return (
    <TeamsList
      teams={teams}
      onTeamSelect={handleTeamSelect}
      onBackToHome={() => navigate('/')}
      onStart={() => startMutation.mutate()}
      onCancel={() => cancelMutation.mutate()}
      onRecompute={() => recomputeMutation.mutate()}
      onClear={type => clearMutation.mutate(type)}
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

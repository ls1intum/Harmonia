import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { PlayCircle, Loader2 } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import { useMutation } from '@tanstack/react-query';
import { AttendanceResourceApi, Configuration } from '@/app/generated';
import FileUpload from '@/components/FileUpload.tsx';

interface StartAnalysisProps {
  onStart: (course: string, exercise: string, username: string, password: string) => void;
}

// Initialize API client for attendance upload
const apiConfig = new Configuration({
  basePath: window.location.origin,
  baseOptions: {
    withCredentials: true,
  },
});
const attendanceApi = new AttendanceResourceApi(apiConfig);

/**
 * Login form component for starting repository analysis.
 * Step 1: User enters Artemis credentials and exercise URL.
 * Step 2: Upon successful login, uploads attendance file if provided.
 * Step 3: Triggers navigation to Teams page.
 */
const StartAnalysis = ({ onStart }: StartAnalysisProps) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [exerciseUrl, setExerciseUrl] = useState('https://artemis.tum.de/courses/478/exercises/18806');
  const [attendanceFile, setAttendanceFile] = useState<File | null>(null);

  // Parse exercise URL to extract baseUrl, courseId, and exerciseId
  const parseExerciseUrl = (urlString: string) => {
    try {
      const url = new URL(urlString);
      const baseUrl = `${url.protocol}//${url.host}`;
      const path = url.pathname;
      const regex = /\/courses\/(\d+)\/exercises\/(\d+)/i;
      const match = path.match(regex);
      if (!match) return null;
      return { baseUrl, courseId: match[1], exerciseId: match[2] };
    } catch {
      return null;
    }
  };

  // Mutation for uploading attendance file
  const attendanceUploadMutation = useMutation({
    mutationFn: async (params: {
      courseId: number;
      exerciseId: number;
      file: File;
      serverUrl: string;
      username: string;
      password: string;
    }) => {
      return attendanceApi.uploadAttendance(
        params.courseId,
        params.exerciseId,
        params.file,
        undefined, // jwt (cookie)
        undefined, // artemisServerUrl (cookie)
        undefined, // artemisUsername (cookie)
        undefined, // artemisPassword (cookie)
        params.serverUrl,
        params.username,
        params.password,
      );
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'Attendance upload failed',
        description: 'Could not process the attendance file. Analysis will continue without it.',
      });
    },
  });

  // Mutation for login
  const loginMutation = useMutation({
    mutationFn: async (params: { username: string; password: string; serverUrl: string; courseId: string }) => {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
      });

      if (!response.ok) {
        const error = new Error('Login failed');
        (error as Error & { status: number }).status = response.status;
        throw error;
      }
      return response;
    },
  });

  const handleStart = async () => {
    const parsed = parseExerciseUrl(exerciseUrl.trim());
    if (!parsed) {
      toast({
        variant: 'destructive',
        title: 'Invalid URL',
        description: 'Please provide a valid Artemis exercise URL like https://.../courses/30/exercises/282',
      });
      return;
    }

    const { baseUrl, courseId, exerciseId } = parsed;

    try {
      // Step 1: Login
      await loginMutation.mutateAsync({
        username,
        password,
        serverUrl: baseUrl,
        courseId,
      });

      // Step 2: Upload attendance file if provided (after successful login)
      if (attendanceFile) {
        try {
          await attendanceUploadMutation.mutateAsync({
            courseId: parseInt(courseId),
            exerciseId: parseInt(exerciseId),
            file: attendanceFile,
            serverUrl: baseUrl,
            username,
            password,
          });
          toast({
            title: 'Attendance uploaded',
            description: 'Pair programming attendance data was successfully processed.',
          });
        } catch {
          // Error already handled in mutation onError, continue with analysis
        }
      }

      // Step 3: Navigate to analysis
      onStart(courseId, exerciseId, username, password);
    } catch (error) {
      const status = (error as Error & { status?: number }).status;
      if (status === 403) {
        toast({
          variant: 'destructive',
          title: 'Access denied',
          description: 'Your user is not listed as an instructor for the specified course.',
        });
      } else {
        toast({
          variant: 'destructive',
          title: 'Login failed',
          description: 'Please check your credentials and try again.',
        });
      }
    }
  };

  const isLoading = loginMutation.isPending || attendanceUploadMutation.isPending;

  return (
    <div className="flex flex-col items-center justify-center min-h-screen gap-6 px-4">
      <div className="text-center space-y-4 max-w-2xl">
        <h1 className="text-4xl md:text-5xl font-bold bg-gradient-primary bg-clip-text">Welcome to Harmonia!</h1>
        <p className="text-lg text-muted-foreground">Analyze student team projects to assess collaboration quality</p>
      </div>

      <div className="w-full max-w-md space-y-4 mt-4">
        <div className="space-y-2">
          <Label htmlFor="exerciseUrl">Artemis Exercise URL</Label>
          <Input
            id="exerciseUrl"
            placeholder="https://artemis.../courses/30/exercises/282"
            value={exerciseUrl}
            onChange={e => setExerciseUrl(e.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="username">Artemis Username</Label>
          <Input
            id="username"
            placeholder="Enter your username"
            value={username}
            onChange={e => setUsername(e.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="password">Artemis Password</Label>
          <Input
            id="password"
            type="password"
            placeholder="Enter your password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            disabled={isLoading}
          />
        </div>

        <div className="my-6 border-t border-border" />

        {/* Pair Programming Attendance Upload */}
        <FileUpload onFileSelect={setAttendanceFile} disabled={isLoading} />

        <Button
          size="lg"
          onClick={handleStart}
          disabled={!exerciseUrl || !username || !password || isLoading}
          className="w-full mt-6 text-lg px-8 py-6 shadow-elevated hover:shadow-card transition-all"
        >
          {isLoading ? <Loader2 className="mr-2 h-5 w-5 animate-spin" /> : <PlayCircle className="mr-2 h-5 w-5" />}
          {attendanceUploadMutation.isPending ? 'Uploading Attendance...' : loginMutation.isPending ? 'Logging in...' : 'Login'}
        </Button>
      </div>
    </div>
  );
};

export default StartAnalysis;

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { PlayCircle, Loader2 } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import FileUpload from '@/components/FileUpload.tsx';

interface StartAnalysisProps {
  onStart: (course: string, exercise: string, username: string, password: string) => void;
}

/**
 * Login form component for starting repository analysis.
 * Step 1: User enters Artemis credentials and exercise URL.
 * Step 2: Upon successful login, triggers navigation to Teams page.
 */
const StartAnalysis = ({ onStart }: StartAnalysisProps) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [exerciseUrl, setExerciseUrl] = useState('https://artemis.tum.de/courses/478/exercises/18806');
  const [isLoading, setIsLoading] = useState(false);

  // Step 1: Parse exercise URL to extract baseUrl, courseId, and exerciseId
  const parseExerciseUrl = (urlString: string) => {
    try {
      const url = new URL(urlString);
      const baseUrl = `${url.protocol}//${url.host}`;
      const path = url.pathname; // e.g. /courses/30/exercises/282
      const regex = /\/courses\/(\d+)\/exercises\/(\d+)/i;
      const match = path.match(regex);
      if (!match) return null;
      return { baseUrl, courseId: match[1], exerciseId: match[2] };
    } catch {
      return null;
    }
  };

  // Step 2: Handle login and trigger analysis start
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

    setIsLoading(true);
    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ username, password, serverUrl: baseUrl, courseId }),
      });

      if (response.ok) {
        onStart(courseId, exerciseId, username, password);
      } else if (response.status === 403) {
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
    } catch {
      toast({
        variant: 'destructive',
        title: 'Error',
        description: 'An error occurred during login.',
      });
    } finally {
      setIsLoading(false);
    }
  };

  const parsedCourseId = parseExerciseUrl(exerciseUrl.trim())?.courseId;

  return (
    <div className="flex flex-col items-center justify-center min-h-screen gap-6 px-4">
      <div className="text-center space-y-4 max-w-2xl">
        <h1 className="text-4xl md:text-5xl font-bold bg-gradient-primary bg-clip-text">Welcome to Harmonia!</h1>
        <p className="text-lg text-muted-foreground">Analyze student team projects to assess collaboration quality</p>
      </div>

      <div className="w-full max-w-md space-y-4 mt-4">
        {/* Exercise URL */}
        <div className="space-y-2">
          <Label htmlFor="exerciseUrl">Exercise URL</Label>
          <Input
            id="exerciseUrl"
            type="url"
            placeholder="https://artemis.tum.de/courses/30/exercises/282"
            value={exerciseUrl}
            onChange={e => setExerciseUrl(e.target.value)}
          />
        </div>

        {/* Username */}
        <div className="space-y-2">
          <Label htmlFor="username">Username</Label>
          <Input
            id="username"
            type="text"
            placeholder="Enter your username"
            value={username}
            onChange={e => setUsername(e.target.value)}
          />
        </div>

        {/* Password */}
        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <Input
            id="password"
            type="password"
            placeholder="Enter your password"
            value={password}
            onChange={e => setPassword(e.target.value)}
          />
        </div>

        <div className="my-6 border-t border-border" />

        {/* Pair Programming Attendance Upload */}
        <FileUpload courseId={parsedCourseId} />

        <Button
          size="lg"
          onClick={handleStart}
          disabled={!exerciseUrl || !username || !password || isLoading}
          className="w-full mt-6 text-lg px-8 py-6 shadow-elevated hover:shadow-card transition-all"
        >
          {isLoading ? <Loader2 className="mr-2 h-5 w-5 animate-spin" /> : <PlayCircle className="mr-2 h-5 w-5" />}
          Login
        </Button>
      </div>
    </div>
  );
};

export default StartAnalysis;

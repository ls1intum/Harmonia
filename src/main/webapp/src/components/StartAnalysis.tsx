import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { PlayCircle, Loader2 } from 'lucide-react';
import { toast } from '@/hooks/use-toast';

interface StartAnalysisProps {
  onStart: (course: string, exercise: string, username?: string, password?: string) => void;
}

const StartAnalysis = ({ onStart }: StartAnalysisProps) => {
  const [course, setCourse] = useState('ITP');
  const [exercise, setExercise] = useState('Final Project');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [serverUrl, setServerUrl] = useState('https://artemis.tum.de');
  const [isLoading, setIsLoading] = useState(false);

  const handleCourseChange = (value: string) => {
    setCourse(value);
    setExercise(''); // Reset exercise when course changes
  };

  const handleStart = async () => {
    if (course && exercise && username && password && serverUrl) {
      setIsLoading(true);
      try {
        const response = await fetch('/api/auth/login', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ username, password, serverUrl }),
        });

        if (response.ok) {
          onStart(course, exercise, username, password);
        } else {
          toast({
            variant: 'destructive',
            title: 'Login failed',
            description: 'Please check your credentials and server URL.',
          });
        }
      } catch (error) {
        toast({
          variant: 'destructive',
          title: 'Error',
          description: 'An error occurred during login.',
        });
      } finally {
        setIsLoading(false);
      }
    }
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-screen gap-6 px-4">
      <div className="text-center space-y-4 max-w-2xl">
        <h1 className="text-4xl md:text-5xl font-bold bg-gradient-primary bg-clip-text">Welcome to Harmonia!</h1>
        <p className="text-lg text-muted-foreground">Analyze student team projects to assess collaboration quality</p>
      </div>

      <div className="w-full max-w-md space-y-4 mt-4">
        <div className="space-y-2">
          <Label htmlFor="serverUrl">Artemis Server URL</Label>
          <Input
            id="serverUrl"
            placeholder="https://artemis.cit.tum.de"
            value={serverUrl}
            onChange={(e) => setServerUrl(e.target.value)}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="username">Artemis Username</Label>
          <Input
            id="username"
            placeholder="Enter your username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="password">Artemis Password</Label>
          <Input
            id="password"
            type="password"
            placeholder="Enter your password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>

        <div className="my-6 border-t border-border" />

        <div className="space-y-2">
          <Label htmlFor="course">Course</Label>
          <Select value={course} onValueChange={handleCourseChange}>
            <SelectTrigger id="course">
              <SelectValue placeholder="Select a course" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ITP">ITP</SelectItem>
              <SelectItem value="DevOps">DevOps</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label htmlFor="exercise">Exercise</Label>
          <Select value={exercise} onValueChange={setExercise} disabled={!course}>
            <SelectTrigger id="exercise">
              <SelectValue placeholder="Select an exercise" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="Final Project">Final Project</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <Button
          size="lg"
          onClick={handleStart}
          disabled={!course || !exercise || !username || !password || !serverUrl || isLoading}
          className="w-full mt-4 text-lg px-8 py-6 shadow-elevated hover:shadow-card transition-all"
        >
          {isLoading ? <Loader2 className="mr-2 h-5 w-5 animate-spin" /> : <PlayCircle className="mr-2 h-5 w-5" />}
          Start Analysis
        </Button>
      </div>
    </div>
  );
};

export default StartAnalysis;

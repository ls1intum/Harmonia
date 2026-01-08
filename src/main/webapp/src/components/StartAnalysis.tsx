import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { PlayCircle, Loader2 } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import { fetchProjectProfiles } from '@/data/configLoader';
import { useQuery } from '@tanstack/react-query';

interface StartAnalysisProps {
  onStart: (course: string, exercise: string, username: string, password: string) => void;
}

const StartAnalysis = ({ onStart }: StartAnalysisProps) => {
  const [selectedProjectId, setSelectedProjectId] = useState<string>('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [serverUrl, setServerUrl] = useState('https://artemis.tum.de');
  const [isLoading, setIsLoading] = useState(false);

  const { data: projects = [] } = useQuery({
    queryKey: ['projects'],
    queryFn: fetchProjectProfiles,
    staleTime: 5 * 60 * 1000, // 5 minutes
    meta: {
      onError: () => {
        toast({
          variant: 'destructive',
          title: 'Failed to load projects',
          description: 'Could not fetch project profiles from server.',
        });
      },
    },
  });

  useEffect(() => {
    if (projects.length > 0 && !selectedProjectId) {
      setSelectedProjectId(projects[0].id);
    }
  }, [projects, selectedProjectId]);

  const handleStart = async () => {
    const selectedProject = projects.find(p => p.id === selectedProjectId);
    if (selectedProject && username && password && serverUrl) {
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
          // Pass course name and exercise ID (as string)
          onStart(selectedProject.courseName, selectedProject.exerciseId.toString(), username, password);
        } else {
          toast({
            variant: 'destructive',
            title: 'Login failed',
            description: 'Please check your credentials and server URL.',
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
          <Input id="serverUrl" placeholder="https://artemis.cit.tum.de" value={serverUrl} onChange={e => setServerUrl(e.target.value)} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="username">Artemis Username</Label>
          <Input id="username" placeholder="Enter your username" value={username} onChange={e => setUsername(e.target.value)} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="password">Artemis Password</Label>
          <Input
            id="password"
            type="password"
            placeholder="Enter your password"
            value={password}
            onChange={e => setPassword(e.target.value)}
          />
        </div>

        <div className="my-6 border-t border-border" />

        <div className="space-y-2">
          <Label htmlFor="project">Project</Label>
          <Select value={selectedProjectId} onValueChange={setSelectedProjectId}>
            <SelectTrigger id="project">
              <SelectValue placeholder="Select a project" />
            </SelectTrigger>
            <SelectContent>
              {projects.map(project => (
                <SelectItem key={project.id} value={project.id}>
                  {project.courseName} - {project.semester} (Exercise ID: {project.exerciseId})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <Button
          size="lg"
          onClick={handleStart}
          disabled={!selectedProjectId || !username || !password || !serverUrl || isLoading}
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

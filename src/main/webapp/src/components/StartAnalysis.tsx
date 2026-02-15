import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { PlayCircle, Loader2, RefreshCw, Cpu } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import FileUpload from '@/components/FileUpload.tsx';

interface StartAnalysisProps {
  onStart: (course: string, exercise: string, username: string, password: string) => void;
}

interface LLMModel {
  id: string;
  object: string;
  owned_by: string;
}

const StartAnalysis = ({ onStart }: StartAnalysisProps) => {
  const queryClient = useQueryClient();
  const [selectedModel, setSelectedModel] = useState<string>('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [exerciseUrl, setExerciseUrl] = useState('https://artemis.tum.de/courses/478/exercises/18806');
  const [isLoading, setIsLoading] = useState(false);

  // Fetch available models from LLM server
  const {
    data: modelsData,
    isLoading: isLoadingModels,
    refetch: refetchModels,
  } = useQuery({
    queryKey: ['llm-models'],
    queryFn: async () => {
      const response = await fetch('/api/ai/models');
      if (!response.ok) throw new Error('Failed to fetch models');
      const data = await response.json();
      return data.data as LLMModel[];
    },
    staleTime: 30 * 1000, // 30 seconds
  });

  // Fetch current model selection
  const { data: currentModelData } = useQuery({
    queryKey: ['current-model'],
    queryFn: async () => {
      const response = await fetch('/api/ai/model');
      if (!response.ok) throw new Error('Failed to fetch current model');
      return response.json();
    },
  });

  // Set initial model from server
  useEffect(() => {
    if (currentModelData?.model && !selectedModel) {
      setSelectedModel(currentModelData.model);
    }
  }, [currentModelData, selectedModel]);

  // Auto-select first model if none selected
  useEffect(() => {
    if (modelsData && modelsData.length > 0 && !selectedModel) {
      setSelectedModel(modelsData[0].id);
    }
  }, [modelsData, selectedModel]);

  const handleModelChange = async (modelId: string) => {
    setSelectedModel(modelId);
    try {
      const response = await fetch('/api/ai/model', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ model: modelId }),
      });
      if (response.ok) {
        toast({
          title: 'Model updated',
          description: `Now using ${modelId} for analysis.`,
        });
        queryClient.invalidateQueries({ queryKey: ['current-model'] });
      }
    } catch {
      toast({
        variant: 'destructive',
        title: 'Failed to update model',
        description: 'Could not change the AI model.',
      });
    }
  };

  // helper: parse exercise URL -> { baseUrl, courseId, exerciseId }
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
  const parsedExerciseId = parseExerciseUrl(exerciseUrl.trim())?.exerciseId;
  console.log('parsedCourseId', parsedCourseId);
  console.log('parsedExerciseId', parsedExerciseId);

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
          <Input id="username" type="text" placeholder="Enter your username" value={username} onChange={e => setUsername(e.target.value)} />
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

        {/* AI Model Selector */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <Label htmlFor="model" className="flex items-center gap-2">
              <Cpu className="h-4 w-4" />
              AI Model
            </Label>
            <Button variant="ghost" size="sm" onClick={() => refetchModels()} disabled={isLoadingModels} className="h-8 px-2">
              <RefreshCw className={`h-4 w-4 ${isLoadingModels ? 'animate-spin' : ''}`} />
            </Button>
          </div>
          <Select value={selectedModel} onValueChange={handleModelChange} disabled={isLoadingModels}>
            <SelectTrigger id="model">
              <SelectValue placeholder={isLoadingModels ? 'Loading models...' : 'Select a model'} />
            </SelectTrigger>
            <SelectContent>
              {modelsData?.map((model: LLMModel) => (
                <SelectItem key={model.id} value={model.id}>
                  {model.id}
                </SelectItem>
              ))}
              {(!modelsData || modelsData.length === 0) && !isLoadingModels && (
                <SelectItem value="none" disabled>
                  No models available
                </SelectItem>
              )}
            </SelectContent>
          </Select>
        </div>

        <div className="my-6 border-t border-border" />

        {/* Pair Programming Attendance Upload */}
        <FileUpload courseId={parsedCourseId} exerciseId={parsedExerciseId} />

        <Button
          size="lg"
          onClick={handleStart}
          disabled={!exerciseUrl || !username || !password || isLoading}
          className="w-full mt-4 text-lg px-8 py-6 shadow-elevated hover:shadow-card transition-all"
        >
          {isLoading ? <Loader2 className="mr-2 h-5 w-5 animate-spin" /> : <PlayCircle className="mr-2 h-5 w-5" />}
          Login
        </Button>
      </div>
    </div>
  );
};

export default StartAnalysis;

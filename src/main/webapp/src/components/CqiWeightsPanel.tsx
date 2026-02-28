import { useReducer } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { toast } from '@/hooks/use-toast';
import { Settings, RotateCcw } from 'lucide-react';

interface CqiWeightsData {
  effortBalance: number;
  locBalance: number;
  temporalSpread: number;
  ownershipSpread: number;
  isDefault: boolean;
}

interface CqiWeightsPanelProps {
  exerciseId: string;
  disabled?: boolean;
}

interface WeightState {
  effort: number;
  loc: number;
  temporal: number;
  ownership: number;
}

type WeightAction =
  | { type: 'SET_EFFORT'; value: number }
  | { type: 'SET_LOC'; value: number }
  | { type: 'SET_TEMPORAL'; value: number }
  | { type: 'SET_OWNERSHIP'; value: number }
  | { type: 'RESET'; state: WeightState };

function weightReducer(state: WeightState, action: WeightAction): WeightState {
  const clamp = (v: number) => Math.max(0, Math.min(100, v));
  switch (action.type) {
    case 'SET_EFFORT':
      return { ...state, effort: clamp(action.value) };
    case 'SET_LOC':
      return { ...state, loc: clamp(action.value) };
    case 'SET_TEMPORAL':
      return { ...state, temporal: clamp(action.value) };
    case 'SET_OWNERSHIP':
      return { ...state, ownership: clamp(action.value) };
    case 'RESET':
      return action.state;
  }
}

function toPercentState(data: CqiWeightsData): WeightState {
  return {
    effort: Math.round(data.effortBalance * 100),
    loc: Math.round(data.locBalance * 100),
    temporal: Math.round(data.temporalSpread * 100),
    ownership: Math.round(data.ownershipSpread * 100),
  };
}

const DEFAULT_STATE: WeightState = { effort: 55, loc: 25, temporal: 5, ownership: 15 };

export default function CqiWeightsPanel({ exerciseId, disabled }: CqiWeightsPanelProps) {
  const queryClient = useQueryClient();
  const [state, dispatch] = useReducer(weightReducer, DEFAULT_STATE);

  const { data: weights, isLoading } = useQuery<CqiWeightsData>({
    queryKey: ['cqiWeights', exerciseId],
    queryFn: async () => {
      const response = await fetch(`/api/exercises/${exerciseId}/cqi-weights`, {
        credentials: 'include',
      });
      if (!response.ok) throw new Error('Failed to fetch CQI weights');
      const data: CqiWeightsData = await response.json();
      dispatch({ type: 'RESET', state: toPercentState(data) });
      return data;
    },
    enabled: !!exerciseId,
  });

  const total = state.effort + state.loc + state.temporal + state.ownership;
  const isValid = total === 100 && state.effort >= 0 && state.loc >= 0 && state.temporal >= 0 && state.ownership >= 0;

  const saveMutation = useMutation({
    mutationFn: async () => {
      const response = await fetch(`/api/exercises/${exerciseId}/cqi-weights`, {
        method: 'PUT',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          effortBalance: state.effort / 100,
          locBalance: state.loc / 100,
          temporalSpread: state.temporal / 100,
          ownershipSpread: state.ownership / 100,
        }),
      });
      if (!response.ok) throw new Error('Failed to save weights');
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cqiWeights', exerciseId] });
      toast({ title: 'CQI weights saved' });
    },
    onError: () => {
      toast({ title: 'Failed to save weights', variant: 'destructive' });
    },
  });

  const resetMutation = useMutation({
    mutationFn: async () => {
      const response = await fetch(`/api/exercises/${exerciseId}/cqi-weights`, {
        method: 'DELETE',
        credentials: 'include',
      });
      if (!response.ok) throw new Error('Failed to reset weights');
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cqiWeights', exerciseId] });
      toast({ title: 'CQI weights reset to defaults' });
    },
    onError: () => {
      toast({ title: 'Failed to reset weights', variant: 'destructive' });
    },
  });

  if (isLoading) return null;

  const fields = [
    { label: 'Effort Balance', value: state.effort, action: 'SET_EFFORT' as const },
    { label: 'LoC Balance', value: state.loc, action: 'SET_LOC' as const },
    { label: 'Temporal Spread', value: state.temporal, action: 'SET_TEMPORAL' as const },
    { label: 'Ownership Spread', value: state.ownership, action: 'SET_OWNERSHIP' as const },
  ];

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-sm font-medium">
            <Settings className="h-4 w-4" />
            CQI Weights
          </CardTitle>
          <Badge variant={weights?.isDefault ? 'secondary' : 'default'}>{weights?.isDefault ? 'Default' : 'Custom'}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          {fields.map(({ label, value, action }) => (
            <div key={label} className="space-y-1">
              <Label className="text-xs">{label}</Label>
              <div className="flex items-center gap-1">
                <Input
                  type="number"
                  min={0}
                  max={100}
                  value={value}
                  onChange={e => dispatch({ type: action, value: parseInt(e.target.value) || 0 })}
                  disabled={disabled}
                  className="h-8 text-sm"
                />
                <span className="text-xs text-muted-foreground">%</span>
              </div>
            </div>
          ))}
        </div>

        <div className={`text-xs font-medium ${isValid ? 'text-muted-foreground' : 'text-destructive'}`}>
          Total: {total}% {!isValid && '(must equal 100%)'}
        </div>

        <div className="flex gap-2">
          <Button
            size="sm"
            onClick={() => saveMutation.mutate()}
            disabled={disabled || !isValid || saveMutation.isPending}
            className="flex-1"
          >
            Save
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => resetMutation.mutate()}
            disabled={disabled || weights?.isDefault || resetMutation.isPending}
          >
            <RotateCcw className="h-3 w-3 mr-1" />
            Reset
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

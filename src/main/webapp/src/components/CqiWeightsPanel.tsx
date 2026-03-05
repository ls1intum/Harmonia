import { useReducer, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { toast } from '@/hooks/use-toast';
import { Settings, RotateCcw, ChevronRight } from 'lucide-react';
import type { CqiWeightsDTO } from '@/app/generated';
import { cqiWeightsApi } from '@/lib/apiClient';

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
  const clamp = (v: number) => (Number.isNaN(v) ? v : Math.max(0, Math.min(100, v)));
  switch (action.type) {
    case 'SET_EFFORT':
      return Object.assign({}, state, { effort: clamp(action.value) });
    case 'SET_LOC':
      return Object.assign({}, state, { loc: clamp(action.value) });
    case 'SET_TEMPORAL':
      return Object.assign({}, state, { temporal: clamp(action.value) });
    case 'SET_OWNERSHIP':
      return Object.assign({}, state, { ownership: clamp(action.value) });
    case 'RESET':
      return action.state;
  }
}

function toPercentState(data: CqiWeightsDTO): WeightState {
  return {
    effort: Math.round((data.effortBalance ?? 0) * 100),
    loc: Math.round((data.locBalance ?? 0) * 100),
    temporal: Math.round((data.temporalSpread ?? 0) * 100),
    ownership: Math.round((data.ownershipSpread ?? 0) * 100),
  };
}

const DEFAULT_STATE: WeightState = { effort: 55, loc: 25, temporal: 5, ownership: 15 };

export default function CqiWeightsPanel({ exerciseId, disabled }: CqiWeightsPanelProps) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [state, dispatch] = useReducer(weightReducer, DEFAULT_STATE);

  const { data: weights, isLoading } = useQuery<CqiWeightsDTO>({
    queryKey: ['cqiWeights', exerciseId],
    queryFn: async () => {
      const response = await cqiWeightsApi.getWeights(parseInt(exerciseId));
      const data = response.data;
      dispatch({ type: 'RESET', state: toPercentState(data) });
      return data;
    },
    enabled: !!exerciseId,
  });

  const val = (n: number) => (Number.isNaN(n) ? 0 : n);
  const total = val(state.effort) + val(state.loc) + val(state.temporal) + val(state.ownership);
  const allFilled =
    !Number.isNaN(state.effort) && !Number.isNaN(state.loc) && !Number.isNaN(state.temporal) && !Number.isNaN(state.ownership);
  const isValid = allFilled && total === 100;

  const saveMutation = useMutation({
    mutationFn: async () => {
      const response = await cqiWeightsApi.saveWeights(parseInt(exerciseId), {
        effortBalance: val(state.effort) / 100,
        locBalance: val(state.loc) / 100,
        temporalSpread: val(state.temporal) / 100,
        ownershipSpread: val(state.ownership) / 100,
      });
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cqiWeights', exerciseId] });
      queryClient.invalidateQueries({ queryKey: ['teams', exerciseId] });
      toast({ title: 'CQI weights saved' });
    },
    onError: (error: unknown) => {
      const axiosError = error as { response?: { data?: string } };
      toast({
        title: 'Failed to save CQI weights',
        description: axiosError.response?.data || 'An unexpected error occurred',
        variant: 'destructive',
      });
    },
  });

  const resetMutation = useMutation({
    mutationFn: async () => {
      const response = await cqiWeightsApi.resetWeights(parseInt(exerciseId));
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cqiWeights', exerciseId] });
      queryClient.invalidateQueries({ queryKey: ['teams', exerciseId] });
      toast({ title: 'CQI weights reset to defaults' });
    },
    onError: (error: unknown) => {
      const axiosError = error as { response?: { data?: string } };
      toast({
        title: 'Failed to reset CQI weights',
        description: axiosError.response?.data || 'An unexpected error occurred',
        variant: 'destructive',
      });
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
    <Collapsible open={open} onOpenChange={setOpen}>
      <Card>
        <CollapsibleTrigger asChild>
          <CardHeader className="py-4 cursor-pointer hover:bg-muted/50 transition-colors">
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2 text-sm font-medium">
                <ChevronRight className={`h-4 w-4 transition-transform ${open ? 'rotate-90' : ''}`} />
                <Settings className="h-4 w-4" />
                CQI Weights
              </CardTitle>
              <Badge variant={weights?.isDefault ? 'secondary' : 'default'}>{weights?.isDefault ? 'Default' : 'Custom'}</Badge>
            </div>
          </CardHeader>
        </CollapsibleTrigger>
        <CollapsibleContent>
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
                      value={Number.isNaN(value) ? '' : value}
                      onChange={e => dispatch({ type: action, value: parseInt(e.target.value) })}
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
        </CollapsibleContent>
      </Card>
    </Collapsible>
  );
}

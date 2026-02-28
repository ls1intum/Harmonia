import { useState, useEffect } from 'react';
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

export default function CqiWeightsPanel({ exerciseId, disabled }: CqiWeightsPanelProps) {
  const queryClient = useQueryClient();

  const { data: weights, isLoading } = useQuery<CqiWeightsData>({
    queryKey: ['cqiWeights', exerciseId],
    queryFn: async () => {
      const response = await fetch(`/api/exercises/${exerciseId}/cqi-weights`, {
        credentials: 'include',
      });
      if (!response.ok) throw new Error('Failed to fetch CQI weights');
      return response.json();
    },
    enabled: !!exerciseId,
  });

  const [effort, setEffort] = useState(55);
  const [loc, setLoc] = useState(25);
  const [temporal, setTemporal] = useState(5);
  const [ownership, setOwnership] = useState(15);

  useEffect(() => {
    if (weights) {
      setEffort(Math.round(weights.effortBalance * 100));
      setLoc(Math.round(weights.locBalance * 100));
      setTemporal(Math.round(weights.temporalSpread * 100));
      setOwnership(Math.round(weights.ownershipSpread * 100));
    }
  }, [weights]);

  const total = effort + loc + temporal + ownership;
  const isValid = total === 100 && effort >= 0 && loc >= 0 && temporal >= 0 && ownership >= 0;

  const saveMutation = useMutation({
    mutationFn: async () => {
      const response = await fetch(`/api/exercises/${exerciseId}/cqi-weights`, {
        method: 'PUT',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          effortBalance: effort / 100,
          locBalance: loc / 100,
          temporalSpread: temporal / 100,
          ownershipSpread: ownership / 100,
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
    { label: 'Effort Balance', value: effort, setter: setEffort },
    { label: 'LoC Balance', value: loc, setter: setLoc },
    { label: 'Temporal Spread', value: temporal, setter: setTemporal },
    { label: 'Ownership Spread', value: ownership, setter: setOwnership },
  ] as const;

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-sm font-medium">
            <Settings className="h-4 w-4" />
            CQI Weights
          </CardTitle>
          <Badge variant={weights?.isDefault ? 'secondary' : 'default'}>
            {weights?.isDefault ? 'Default' : 'Custom'}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          {fields.map(({ label, value, setter }) => (
            <div key={label} className="space-y-1">
              <Label className="text-xs">{label}</Label>
              <div className="flex items-center gap-1">
                <Input
                  type="number"
                  min={0}
                  max={100}
                  value={value}
                  onChange={(e) => setter(Math.max(0, Math.min(100, parseInt(e.target.value) || 0)))}
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

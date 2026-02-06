import { Loader2, CheckCircle, XCircle, Search, Ban } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

export interface AnalysisStatus {
  state: 'IDLE' | 'RUNNING' | 'CANCELLED' | 'DONE' | 'ERROR';
  totalTeams: number;
  processedTeams: number;
  currentTeamName?: string;
  currentStage?: string;
  errorMessage?: string;
}

interface ActivityLogProps {
  status: AnalysisStatus;
}

/**
 * Collapsible activity log showing current analysis progress.
 * Shows current team being analyzed and stage (downloading/analyzing).
 */
export function ActivityLog({ status }: ActivityLogProps) {
  // Don't show anything if idle
  if (status.state === 'IDLE') {
    return null;
  }

  const getStatusIcon = () => {
    switch (status.state) {
      case 'RUNNING':
        return <Loader2 className="h-4 w-4 animate-spin text-primary" />;
      case 'CANCELLED':
        return <Ban className="h-4 w-4 text-muted-foreground" />;
      case 'DONE':
        return <CheckCircle className="h-4 w-4 text-success" />;
      case 'ERROR':
        return <XCircle className="h-4 w-4 text-destructive" />;
      default:
        return null;
    }
  };

  const getStatusText = () => {
    switch (status.state) {
      case 'RUNNING':
        return `Analyzing ${status.processedTeams} / ${status.totalTeams} teams`;
      case 'CANCELLED':
        return `Cancelled: ${status.processedTeams} / ${status.totalTeams} teams processed`;
      case 'DONE':
        return `Completed: ${status.processedTeams} teams analyzed`;
      case 'ERROR':
        return `Error: ${status.errorMessage || 'Unknown error'}`;
      default:
        return '';
    }
  };

  const progress = status.totalTeams > 0 ? Math.round((status.processedTeams / status.totalTeams) * 100) : 0;

  return (
    <Card className="mb-4 overflow-hidden p-4">
      <div className="flex flex-col gap-4">
        {/* Header Row: Icon | Status Text | Badge */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            {getStatusIcon()}
            <span className="text-sm font-medium">{getStatusText()}</span>
            {status.state === 'RUNNING' && (
              <Badge variant="secondary" className="text-xs">
                {progress}%
              </Badge>
            )}
          </div>
        </div>

        {/* Current Team Info Row - Only visible when running */}
        {status.state === 'RUNNING' && (
          <div className="space-y-2">
            {status.currentTeamName && (
              <div className="flex items-center gap-2 text-sm text-foreground/80">
                <Search className="h-3 w-3" />
                <span>
                  {status.currentStage === 'DOWNLOADING' ? 'Downloading' : 'Analyzing'}:{' '}
                  <span className="font-medium text-foreground">{status.currentTeamName}</span>
                </span>
              </div>
            )}

            {/* Progress Bar */}
            <div className="w-full bg-muted rounded-full h-2">
              <div
                className="bg-primary h-2 rounded-full transition-all duration-300 ease-in-out"
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>
        )}

        {/* Error Message */}
        {status.state === 'ERROR' && (
          <div className="px-3 py-2 bg-destructive/10 rounded text-sm text-destructive">
            {status.errorMessage}
          </div>
        )}
      </div>
    </Card>
  );
}

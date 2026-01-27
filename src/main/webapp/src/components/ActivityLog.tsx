import { useState } from 'react';
import { ChevronDown, ChevronUp, Loader2, CheckCircle, XCircle, Download, Search } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

export interface AnalysisStatus {
  state: 'IDLE' | 'RUNNING' | 'PAUSED' | 'DONE' | 'ERROR';
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
  const [isExpanded, setIsExpanded] = useState(false);

  // Don't show anything if idle
  if (status.state === 'IDLE') {
    return null;
  }

  const getStatusIcon = () => {
    switch (status.state) {
      case 'RUNNING':
        return <Loader2 className="h-4 w-4 animate-spin text-primary" />;
      case 'PAUSED':
        return <Loader2 className="h-4 w-4 text-muted-foreground" />;
      case 'DONE':
        return <CheckCircle className="h-4 w-4 text-success" />;
      case 'ERROR':
        return <XCircle className="h-4 w-4 text-destructive" />;
      default:
        return null;
    }
  };

  const getStageIcon = () => {
    switch (status.currentStage) {
      case 'DOWNLOADING':
        return <Download className="h-3 w-3" />;
      case 'ANALYZING':
        return <Search className="h-3 w-3" />;
      default:
        return null;
    }
  };

  const getStatusText = () => {
    switch (status.state) {
      case 'RUNNING':
        return `Analyzing ${status.processedTeams} / ${status.totalTeams} teams`;
      case 'PAUSED':
        return `Paused: ${status.processedTeams} / ${status.totalTeams} teams processed`;
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
      <Card className="mb-4 overflow-hidden">
        <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="w-full flex items-center justify-between p-3 hover:bg-muted/50 transition-colors"
        >
          <div className="flex items-center gap-3">
            {getStatusIcon()}
            <span className="text-sm font-medium">{getStatusText()}</span>
            {(status.state === 'RUNNING' || status.state === 'PAUSED') && (
                <Badge variant="secondary" className="text-xs">
                  {progress}%
                </Badge>
            )}
          </div>
          <Button variant="ghost" size="sm" className="h-6 w-6 p-0">
            {isExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
          </Button>
        </button>

        {isExpanded && status.state === 'RUNNING' && status.currentTeamName && (
            <div className="px-3 pb-3 pt-0 border-t">
              <div className="flex items-center gap-2 text-sm text-muted-foreground mt-2">
                {getStageIcon()}
                <span>
              {status.currentStage === 'DOWNLOADING' ? 'Downloading' : 'Analyzing'}:{' '}
                  <span className="font-medium text-foreground">{status.currentTeamName}</span>
            </span>
              </div>
              <div className="w-full bg-muted rounded-full h-1.5 mt-2">
                <div className="bg-primary h-1.5 rounded-full transition-all" style={{ width: `${progress}%` }} />
              </div>
            </div>
        )}

        {isExpanded && status.state === 'ERROR' && (
            <div className="px-3 pb-3 pt-0 border-t">
              <p className="text-sm text-destructive mt-2">{status.errorMessage}</p>
            </div>
        )}
      </Card>
  );
}

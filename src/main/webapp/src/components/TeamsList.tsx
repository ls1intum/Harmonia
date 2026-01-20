import type { Team } from '@/types/team';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { AlertTriangle, ArrowLeft, Play, Square, RefreshCw, Trash2 } from 'lucide-react';
import { useState, useMemo } from 'react';
import { SortableHeader } from '@/components/SortableHeader.tsx';
import { StatusFilterButton } from '@/components/StatusFilterButton.tsx';
import { ActivityLog, type AnalysisStatus } from '@/components/ActivityLog';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';

interface TeamsListProps {
  teams: Team[];
  onTeamSelect: (team: Team) => void;
  onBackToHome: () => void;
  onStart: () => void;
  onCancel: () => void;
  onRecompute: () => void;
  onClear: (type: 'db' | 'files' | 'both') => void;
  course: string;
  exercise: string;
  analysisStatus: AnalysisStatus;
}

const TeamsList = ({
  teams,
  onTeamSelect,
  onBackToHome,
  onStart,
  onCancel,
  onRecompute,
  onClear,
  course,
  exercise,
  analysisStatus,
}: TeamsListProps) => {
  const [sortColumn, setSortColumn] = useState<'name' | 'commits' | 'cqi' | null>(null);
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
  const [statusFilter, setStatusFilter] = useState<'all' | 'normal' | 'suspicious'>('all');
  const [clearDialogOpen, setClearDialogOpen] = useState(false);
  const [clearType, setClearType] = useState<'db' | 'files' | 'both'>('both');

  const getCQIColor = (cqi: number) => {
    if (cqi >= 80) return 'text-success';
    if (cqi >= 60) return 'text-warning';
    return 'text-destructive';
  };

  const getCQIBgColor = (cqi: number) => {
    if (cqi >= 80) return 'bg-success/10';
    if (cqi >= 60) return 'bg-warning/10';
    return 'bg-destructive/10';
  };

  const handleHeaderClick = (column: 'name' | 'commits' | 'cqi') => {
    if (sortColumn !== column) {
      setSortColumn(column);
      setSortDirection('asc');
    } else if (sortDirection === 'asc') {
      setSortDirection('desc');
    } else {
      setSortColumn(null);
    }
  };

  const sortedAndFilteredTeams = useMemo(() => {
    let filtered = [...teams];

    // Apply status filter
    if (statusFilter !== 'all') {
      filtered = filtered.filter(team => (statusFilter === 'suspicious' ? team.isSuspicious : !team.isSuspicious));
    }

    // Apply sorting
    if (sortColumn) {
      filtered.sort((a, b) => {
        let comparison = 0;

        if (sortColumn === 'name') {
          comparison = a.teamName.localeCompare(b.teamName);
        } else if (sortColumn === 'commits') {
          const aCommits = a.basicMetrics?.totalCommits || 0;
          const bCommits = b.basicMetrics?.totalCommits || 0;
          comparison = aCommits - bCommits;
        } else if (sortColumn === 'cqi') {
          const aCqi = a.cqi || 0;
          const bCqi = b.cqi || 0;
          comparison = aCqi - bCqi;
        }

        return sortDirection === 'asc' ? comparison : -comparison;
      });
    }

    return filtered;
  }, [teams, sortColumn, sortDirection, statusFilter]);

  const courseAverages = useMemo(() => {
    if (teams.length === 0) return null;

    const totalCQI = teams.reduce((sum, team) => sum + (team.cqi || 0), 0);
    const totalCommits = teams.reduce((sum, team) => sum + (team.basicMetrics?.totalCommits || 0), 0);
    const totalLines = teams.reduce((sum, team) => sum + (team.basicMetrics?.totalLines || 0), 0);
    const suspiciousCount = teams.filter(team => team.isSuspicious).length;

    return {
      avgCQI: Math.round(totalCQI / teams.length),
      avgCommits: Math.round(totalCommits / teams.length),
      avgLines: Math.round(totalLines / teams.length),
      suspiciousPercentage: Math.round((suspiciousCount / teams.length) * 100),
      totalTeams: teams.length,
    };
  }, [teams]);

  const renderActionButton = () => {
    switch (analysisStatus.state) {
      case 'IDLE':
        return (
          <Button onClick={onStart}>
            <Play className="h-4 w-4" />
            Start Analysis
          </Button>
        );
      case 'RUNNING':
        return (
          <Button variant="destructive" onClick={onCancel}>
            <Square className="h-4 w-4" />
            Cancel
          </Button>
        );
      case 'DONE':
      case 'ERROR':
        return (
          <Button variant="secondary" onClick={onRecompute}>
            <RefreshCw className="h-4 w-4" />
            Force Recompute
          </Button>
        );
    }
  };

  const handleClearClick = (type: 'db' | 'files' | 'both') => {
    setClearType(type);
    setClearDialogOpen(true);
  };

  return (
    <div className="space-y-6 px-4 py-8 max-w-7xl mx-auto">
      <Button variant="ghost" onClick={onBackToHome} className="mb-4 hover:bg-muted">
        <ArrowLeft className="mr-2 h-4 w-4" />
        Back to Home
      </Button>

      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="space-y-2">
          <h2 className="text-3xl font-bold">Teams Overview</h2>
          <p className="text-muted-foreground">
            Course: <span className="font-medium">{course}</span> | Exercise: <span className="font-medium">{exercise}</span>
          </p>
          <p className="text-muted-foreground text-sm">Click on any team to view detailed collaboration metrics</p>
        </div>
        <div className="flex gap-2">
          {renderActionButton()}
          <Button variant="outline" onClick={() => handleClearClick('both')} disabled={analysisStatus.state === 'RUNNING'}>
            <Trash2 className="h-4 w-4" />
            Clear Data
          </Button>
        </div>
      </div>

      <ActivityLog status={analysisStatus} />

      <ConfirmationDialog
        open={clearDialogOpen}
        onOpenChange={setClearDialogOpen}
        title="Clear Data"
        description={`This will permanently delete ${clearType === 'both' ? 'database records and repository files' : clearType === 'db' ? 'database records' : 'repository files'}. This action cannot be undone.`}
        confirmLabel="Clear"
        variant="destructive"
        onConfirm={() => onClear(clearType)}
      />

      {courseAverages && (
        <Card className="p-6 shadow-card">
          <h3 className="text-lg font-semibold mb-4">Course Averages</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">Average CQI</p>
              <div className="flex items-baseline gap-2">
                <p className={`text-3xl font-bold ${getCQIColor(courseAverages.avgCQI)}`}>{courseAverages.avgCQI}</p>
                <span className="text-sm text-muted-foreground">/ 100</span>
              </div>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">Average Commits</p>
              <p className="text-3xl font-bold text-foreground">{courseAverages.avgCommits}</p>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">Average Lines</p>
              <p className="text-3xl font-bold text-foreground">{courseAverages.avgLines.toLocaleString()}</p>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">Suspicious Teams</p>
              <div className="flex items-baseline gap-2">
                <p className={`text-3xl font-bold ${courseAverages.suspiciousPercentage > 30 ? 'text-destructive' : 'text-success'}`}>
                  {courseAverages.suspiciousPercentage}%
                </p>
                <span className="text-sm text-muted-foreground">
                  ({teams.filter(t => t.isSuspicious).length} of {courseAverages.totalTeams})
                </span>
              </div>
            </div>
          </div>
        </Card>
      )}

      <Card className="overflow-hidden shadow-card">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-muted/50 border-b">
              <tr>
                <th className="text-left py-4 px-6 font-semibold text-sm">
                  <SortableHeader
                    column="name"
                    label="Team Name"
                    sortColumn={sortColumn}
                    sortDirection={sortDirection}
                    handleHeaderClick={handleHeaderClick}
                  />
                </th>
                <th className="text-left py-4 px-6 font-semibold text-sm">Members</th>
                <th className="text-left py-4 px-6 font-semibold text-sm">
                  <SortableHeader
                    column="commits"
                    label="Commits"
                    sortColumn={sortColumn}
                    sortDirection={sortDirection}
                    handleHeaderClick={handleHeaderClick}
                  />
                </th>
                <th className="text-left py-4 px-6 font-semibold text-sm">
                  <SortableHeader
                    column="cqi"
                    label="CQI Score"
                    sortColumn={sortColumn}
                    sortDirection={sortDirection}
                    handleHeaderClick={handleHeaderClick}
                  />
                </th>
                <th className="text-left py-4 px-6 font-semibold text-sm">
                  <StatusFilterButton statusFilter={statusFilter} setStatusFilter={setStatusFilter} />
                </th>
              </tr>
            </thead>
            <tbody>
              {sortedAndFilteredTeams.map(team => (
                <tr
                  key={team.id}
                  onClick={() => onTeamSelect(team)}
                  className="border-b last:border-b-0 hover:bg-muted/30 cursor-pointer transition-colors"
                >
                  <td className="py-4 px-6">
                    <p className="font-semibold">{team.teamName.replace('Team ', '')}</p>
                  </td>
                  <td className="py-4 px-6">
                    <div className="space-y-1">
                      {team.students.map((student, idx) => (
                        <p key={idx} className="text-sm">
                          {student.name} {student.commits !== undefined && `(${student.commits} commits)`}
                        </p>
                      ))}
                    </div>
                  </td>
                  <td className="py-4 px-6">
                    {team.basicMetrics && (
                      <div className="space-y-1">
                        <p className="font-medium">{team.basicMetrics.totalCommits}</p>
                        <p className="text-xs text-muted-foreground">{team.basicMetrics.totalLines} lines</p>
                      </div>
                    )}
                  </td>
                  <td className="py-4 px-6">
                    {team.cqi !== undefined ? (
                      <div className="flex items-center gap-3">
                        <div className={`flex items-center justify-center w-16 h-16 rounded-lg ${getCQIBgColor(team.cqi)}`}>
                          <span className={`text-2xl font-bold ${getCQIColor(team.cqi)}`}>{team.cqi}</span>
                        </div>
                        <div className="text-xs text-muted-foreground">out of 100</div>
                      </div>
                    ) : (
                      <Skeleton className="h-16 w-32" />
                    )}
                  </td>
                  <td className="py-4 px-6">
                    {team.isSuspicious !== undefined ? (
                      team.isSuspicious ? (
                        <Badge variant="destructive" className="gap-1.5">
                          <AlertTriangle className="h-3 w-3" />
                          Suspicious
                        </Badge>
                      ) : (
                        <Badge variant="secondary" className="bg-success/10 text-success hover:bg-success/20">
                          Normal
                        </Badge>
                      )
                    ) : (
                      <Skeleton className="h-6 w-20" />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};

export default TeamsList;

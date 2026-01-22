import type { Team } from '@/types/team';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ArrowLeft, AlertTriangle, Users, ClipboardCheck, Filter, TrendingDown } from 'lucide-react';
import MetricCard from './MetricCard';
import AnalysisFeed from './AnalysisFeed';
import ErrorListPanel from './ErrorListPanel';
import OrphanCommitsPanel from './OrphanCommitsPanel';

interface TeamDetailProps {
  team: Team;
  onBack: () => void;
  course: string;
  exercise: string;
}

const TeamDetail = ({ team, onBack, course, exercise }: TeamDetailProps) => {
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

  return (
    <div className="space-y-6 px-4 py-8 max-w-7xl mx-auto">
      <Button variant="ghost" onClick={onBack} className="mb-4 hover:bg-muted">
        <ArrowLeft className="mr-2 h-4 w-4" />
        Back to Teams
      </Button>

      <div className="mb-4">
        <p className="text-sm text-muted-foreground">
          Course: <span className="font-medium">{course}</span> | Exercise: <span className="font-medium">{exercise}</span>
        </p>
      </div>

      <Card className="shadow-elevated bg-gradient-card">
        <CardContent className="pt-6">
          <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-6">
            <div className="space-y-4 flex-1">
              <div className="space-y-1 mb-4">
                <h2 className="text-2xl font-bold">{team.teamName}</h2>
                <p className="text-sm text-muted-foreground">Team Details</p>
              </div>

              <div className="flex items-center gap-2">
                <Users className="h-5 w-5 text-primary" />
                <h3 className="text-xl font-bold">Team Members</h3>
              </div>
              <div className="space-y-2 pl-7">
                {team.students.map((student, index) => (
                  <div key={index} className="space-y-0.5">
                    <p className="text-lg font-medium">{student.name}</p>
                    {student.commits !== undefined &&
                      student.linesAdded !== undefined &&
                      student.linesDeleted !== undefined &&
                      student.linesChanged !== undefined && (
                        <p className="text-xs text-muted-foreground">
                          {student.commits} commits • {student.linesChanged} lines changed (
                          <span className="text-green-600">+{student.linesAdded}</span>{' '}
                          <span className="text-red-600">-{student.linesDeleted}</span>)
                        </p>
                      )}
                  </div>
                ))}
              </div>
              <div className="flex items-center gap-2 mt-5">
                <ClipboardCheck className="h-5 w-5 text-primary" />
                <h3 className="text-sm font-medium">Tutor: {team.tutor}</h3>
              </div>
              <div className="pt-2">
                {team.isSuspicious ? (
                  <Badge variant="destructive" className="gap-1.5">
                    <AlertTriangle className="h-3 w-3" />
                    Suspicious Behavior Detected
                  </Badge>
                ) : (
                  <Badge variant="secondary" className="bg-success/10 text-success hover:bg-success/20">
                    Normal Collaboration Pattern
                  </Badge>
                )}
              </div>
            </div>

            <div className="flex flex-col items-center md:items-end gap-2">
              {team.cqi !== undefined && (
                <div className={`flex items-center justify-center w-32 h-32 rounded-2xl ${getCQIBgColor(team.cqi)}`}>
                  <div className="text-center">
                    <div className={`text-5xl font-bold ${getCQIColor(team.cqi)}`}>{team.cqi}</div>
                    <div className="text-xs text-muted-foreground mt-1">CQI Score</div>
                  </div>
                </div>
              )}
              <p className="text-sm text-muted-foreground text-center md:text-right">Collaboration Quality Index</p>
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="space-y-4">
        <div>
          <h3 className="text-2xl font-bold mb-2">Detailed Metrics</h3>
          <p className="text-muted-foreground">
            The CQI is composed of four weighted sub-indices that measure different aspects of team collaboration
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {team.subMetrics && team.subMetrics.map((metric, index) => <MetricCard key={index} metric={metric} />)}
        </div>
      </div>

      {/* Penalties and Filter Summary */}
      {team.cqiDetails && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Penalties */}
          {team.cqiDetails.penalties && team.cqiDetails.penalties.length > 0 && (
            <Card className="shadow-card">
              <CardHeader>
                <div className="flex items-center gap-2">
                  <TrendingDown className="h-5 w-5 text-destructive" />
                  <CardTitle className="text-lg">Applied Penalties</CardTitle>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                <p className="text-sm text-muted-foreground mb-3">
                  Base Score: <span className="font-bold">{Math.round(team.cqiDetails.baseScore)}</span> →{' '}
                  Final: <span className="font-bold">{Math.round(team.cqiDetails.cqi)}</span>{' '}
                  <span className="text-xs">
                    (×{team.cqiDetails.penaltyMultiplier.toFixed(2)})
                  </span>
                </p>
                {team.cqiDetails.penalties.map((penalty, index) => (
                  <div key={index} className="flex items-start gap-2 p-2 bg-destructive/10 rounded-lg">
                    <AlertTriangle className="h-4 w-4 text-destructive mt-0.5 flex-shrink-0" />
                    <div>
                      <p className="text-sm font-medium">{penalty.type.replace(/_/g, ' ')}</p>
                      <p className="text-xs text-muted-foreground">{penalty.reason}</p>
                      <p className="text-xs text-destructive">-{((1 - penalty.factor) * 100).toFixed(0)}% reduction</p>
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>
          )}

          {/* Filter Summary */}
          {team.cqiDetails.filterSummary && (
            <Card className="shadow-card">
              <CardHeader>
                <div className="flex items-center gap-2">
                  <Filter className="h-5 w-5 text-primary" />
                  <CardTitle className="text-lg">Pre-Filter Summary</CardTitle>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground mb-3">
                  <span className="font-bold">{team.cqiDetails.filterSummary.productiveCommits}</span> of{' '}
                  <span className="font-bold">{team.cqiDetails.filterSummary.totalCommits}</span> commits analyzed
                  {team.cqiDetails.filterSummary.totalCommits > 0 && (
                    <span className="text-xs ml-1">
                      ({Math.round((team.cqiDetails.filterSummary.productiveCommits / team.cqiDetails.filterSummary.totalCommits) * 100)}% kept)
                    </span>
                  )}
                </p>
                <div className="grid grid-cols-2 gap-2 text-sm">
                  {team.cqiDetails.filterSummary.mergeCount > 0 && (
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Merge commits:</span>
                      <span>{team.cqiDetails.filterSummary.mergeCount}</span>
                    </div>
                  )}
                  {team.cqiDetails.filterSummary.revertCount > 0 && (
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Revert commits:</span>
                      <span>{team.cqiDetails.filterSummary.revertCount}</span>
                    </div>
                  )}
                  {team.cqiDetails.filterSummary.trivialCount > 0 && (
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Trivial commits:</span>
                      <span>{team.cqiDetails.filterSummary.trivialCount}</span>
                    </div>
                  )}
                  {team.cqiDetails.filterSummary.formatOnlyCount > 0 && (
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Format-only:</span>
                      <span>{team.cqiDetails.filterSummary.formatOnlyCount}</span>
                    </div>
                  )}
                  {team.cqiDetails.filterSummary.autoGeneratedCount > 0 && (
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Auto-generated:</span>
                      <span>{team.cqiDetails.filterSummary.autoGeneratedCount}</span>
                    </div>
                  )}
                  {team.cqiDetails.filterSummary.emptyCount > 0 && (
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Empty commits:</span>
                      <span>{team.cqiDetails.filterSummary.emptyCount}</span>
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      )}

      {/* AI Analysis Feed */}
      <div className="space-y-4">
        <div>
          <h3 className="text-2xl font-bold mb-2">AI Analysis Feed</h3>
          <p className="text-muted-foreground">See exactly how the AI analyzed each commit or group of commits</p>
        </div>

        <ErrorListPanel
          errors={
            team.analysisHistory
              ?.filter(chunk => chunk.isError && chunk.errorMessage)
              .map(chunk => ({
                id: chunk.id,
                authorEmail: chunk.authorEmail,
                timestamp: chunk.timestamp,
                errorMessage: chunk.errorMessage!,
                commitShas: chunk.commitShas,
              })) || []
          }
        />

        <OrphanCommitsPanel commits={team.orphanCommits || []} />

        <AnalysisFeed chunks={team.analysisHistory || []} />
      </div>
    </div>
  );
};

export default TeamDetail;

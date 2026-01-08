import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ChevronDown, ChevronUp, GitCommit, Clock, FileCode } from 'lucide-react';
import type { AnalyzedChunk } from '@/types/team';

interface AnalysisFeedProps {
  chunks: AnalyzedChunk[];
}

const classificationColors: Record<string, string> = {
  FEATURE: 'bg-blue-500/10 text-blue-600',
  BUG_FIX: 'bg-orange-500/10 text-orange-600',
  REFACTOR: 'bg-purple-500/10 text-purple-600',
  TEST: 'bg-green-500/10 text-green-600',
  DOCUMENTATION: 'bg-cyan-500/10 text-cyan-600',
  CONFIG: 'bg-gray-500/10 text-gray-600',
  TRIVIAL: 'bg-gray-400/10 text-gray-500',
};

const AnalysisFeed = ({ chunks }: AnalysisFeedProps) => {
  const [expandedChunks, setExpandedChunks] = useState<Set<string>>(new Set());

  const toggleExpand = (id: string) => {
    setExpandedChunks(prev => {
      const newSet = new Set(prev);
      if (newSet.has(id)) {
        newSet.delete(id);
      } else {
        newSet.add(id);
      }
      return newSet;
    });
  };

  if (!chunks || chunks.length === 0) {
    return (
      <Card className="bg-muted/50">
        <CardContent className="py-8 text-center text-muted-foreground">
          No analysis data available. Recompute to see AI analysis details.
        </CardContent>
      </Card>
    );
  }

  // Group by author for summary
  const authorSummary = chunks.reduce(
    (acc, chunk) => {
      const email = chunk.authorEmail;
      if (!acc[email]) {
        acc[email] = { effort: 0, count: 0 };
      }
      acc[email].effort += chunk.effortScore;
      acc[email].count += 1;
      return acc;
    },
    {} as Record<string, { effort: number; count: number }>,
  );

  const totalEffort = Object.values(authorSummary).reduce((sum, a) => sum + a.effort, 0);

  return (
    <div className="space-y-6">
      {/* Author Summary */}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Effort Distribution</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-4">
            {Object.entries(authorSummary).map(([email, data]) => {
              const percentage = totalEffort > 0 ? Math.round((data.effort / totalEffort) * 100) : 0;
              return (
                <div key={email} className="flex items-center gap-2">
                  <div className="h-3 rounded-full bg-primary" style={{ width: `${Math.max(20, percentage)}px` }} />
                  <span className="text-sm font-medium">{email.split('@')[0]}</span>
                  <span className="text-sm text-muted-foreground">
                    {percentage}% ({data.count} chunks)
                  </span>
                </div>
              );
            })}
          </div>
        </CardContent>
      </Card>

      {/* Analysis Feed */}
      <div className="space-y-3">
        <h3 className="text-lg font-semibold">Analysis History ({chunks.length} chunks)</h3>
        {chunks.map(chunk => {
          const isExpanded = expandedChunks.has(chunk.id);
          const colorClass = classificationColors[chunk.classification] || classificationColors.TRIVIAL;

          return (
            <Card key={chunk.id} className="overflow-hidden">
              <div
                className="flex items-center justify-between p-4 cursor-pointer hover:bg-muted/50"
                onClick={() => toggleExpand(chunk.id)}
              >
                <div className="flex items-center gap-3">
                  <Badge className={colorClass}>{chunk.classification}</Badge>
                  <span className="font-medium">{chunk.authorEmail.split('@')[0]}</span>
                  {chunk.isBundled && (
                    <Badge variant="outline" className="text-xs">
                      Bundled
                    </Badge>
                  )}
                  {chunk.totalChunks > 1 && (
                    <Badge variant="outline" className="text-xs">
                      Part {chunk.chunkIndex + 1}/{chunk.totalChunks}
                    </Badge>
                  )}
                </div>
                <div className="flex items-center gap-4">
                  <span className="text-sm font-semibold text-primary">{chunk.effortScore.toFixed(1)} effort</span>
                  <Button variant="ghost" size="sm">
                    {isExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                  </Button>
                </div>
              </div>

              {isExpanded && (
                <CardContent className="border-t bg-muted/30 pt-4 space-y-3">
                  {/* Reasoning */}
                  <div>
                    <p className="text-sm font-medium text-muted-foreground mb-1">AI Reasoning:</p>
                    <p className="text-sm">{chunk.reasoning || 'No reasoning provided.'}</p>
                  </div>

                  {/* Commit details */}
                  <div className="space-y-2">
                    <p className="text-sm font-medium text-muted-foreground">Commits ({chunk.commitShas.length}):</p>
                    {chunk.commitShas.map((sha, idx) => (
                      <div key={sha} className="flex items-start gap-2 text-sm">
                        <GitCommit className="h-4 w-4 mt-0.5 text-muted-foreground" />
                        <div>
                          <code className="text-xs bg-muted px-1 py-0.5 rounded">{sha.slice(0, 7)}</code>
                          <span className="ml-2">{chunk.commitMessages[idx]}</span>
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Metadata */}
                  <div className="flex gap-4 text-xs text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <FileCode className="h-3 w-3" />
                      {chunk.linesChanged} lines
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock className="h-3 w-3" />
                      {new Date(chunk.timestamp).toLocaleDateString()}
                    </span>
                  </div>
                </CardContent>
              )}
            </Card>
          );
        })}
      </div>
    </div>
  );
};

export default AnalysisFeed;

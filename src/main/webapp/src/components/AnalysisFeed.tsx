import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ChevronDown, ChevronUp, GitCommit, Clock, FileCode, AlertCircle, AlertTriangle, UserX, Info } from 'lucide-react';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import type { AnalyzedChunkDTO } from '@/app/generated';

interface AnalysisFeedProps {
  chunks: AnalyzedChunkDTO[];
  isDevMode?: boolean;
}

// Badge-only colors for classification labels
const badgeColors: Record<string, string> = {
  FEATURE: 'bg-white text-blue-600 border border-blue-200',
  BUG_FIX: 'bg-white text-orange-600 border border-orange-200',
  REFACTOR: 'bg-white text-purple-600 border border-purple-200',
  TEST: 'bg-white text-green-600 border border-green-200',
  DOCUMENTATION: 'bg-white text-cyan-600 border border-cyan-200',
  CONFIG: 'bg-white text-gray-600 border border-gray-200',
  TRIVIAL: 'bg-white text-gray-500 border border-gray-200',
};

// Distinct colors for differentiating authors in the analysis history
const authorPalette = [
  { dot: 'bg-violet-500', border: 'border-l-violet-500', hex: '#8b5cf6' },
  { dot: 'bg-pink-500', border: 'border-l-pink-500', hex: '#ec4899' },
  { dot: 'bg-cyan-500', border: 'border-l-cyan-500', hex: '#06b6d4' },
];

// Returns an HSL color string for effort score: 0 = red, 5 = amber, 10 = green
const getEffortColor = (score: number): string => {
  const clamped = Math.max(0, Math.min(10, score));
  // Hue: 0 (red) -> 45 (amber) -> 120 (green)
  const hue = (clamped / 10) * 120;
  return `hsl(${hue}, 85%, 32%)`;
};

// Metric tooltip descriptions
const metricInfo = {
  effort:
    'Measures the meaningful work in a commit. Higher = more substantial contribution. Range: 0–10. Look for: ≥5 is solid, <3 may indicate trivial/low-effort commits.',
  complexity:
    'Measures the technical difficulty of the code changes. Higher = more complex logic, algorithms, or architecture. Range: 0–10.',
  novelty: 'Measures how much new/original code was introduced vs. boilerplate or copy-paste. Higher = more original work. Range: 0–10.',
  confidence: 'How confident the AI is in its assessment. Higher = more reliable rating.',
};

const MetricLabel = ({ label, tooltip }: { label: string; tooltip: string }) => (
  <TooltipProvider delayDuration={200}>
    <Tooltip>
      <TooltipTrigger asChild>
        <p className="text-xs text-muted-foreground mb-1 inline-flex items-center gap-1 cursor-help">
          {label}
          <Info className="h-3 w-3 text-muted-foreground/60" />
        </p>
      </TooltipTrigger>
      <TooltipContent side="top" className="max-w-xs text-xs">
        {tooltip}
      </TooltipContent>
    </Tooltip>
  </TooltipProvider>
);

const AnalysisFeed = ({ chunks, isDevMode = false }: AnalysisFeedProps) => {
  const [expandedChunks, setExpandedChunks] = useState<Set<string>>(new Set());
  const [externalOpen, setExternalOpen] = useState(false);

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

  // Separate team member chunks from external contributor chunks
  const teamChunks = (chunks ?? []).filter(chunk => !chunk.isExternalContributor);
  const externalChunks = (chunks ?? []).filter(chunk => chunk.isExternalContributor);

  // Group by author for summary (filtering out errors and external contributors)
  const authorSummary = teamChunks
    .filter(chunk => !chunk.isError)
    .reduce(
      (acc, chunk) => {
        const email = chunk.authorEmail ?? 'unknown';
        if (!acc[email]) {
          acc[email] = { name: chunk.authorName ?? email.split('@')[0], effort: 0, complexity: 0, novelty: 0, confidence: 0, count: 0 };
        }
        acc[email].effort += chunk.effortScore ?? 0;
        acc[email].complexity += chunk.complexity ?? 0;
        acc[email].novelty += chunk.novelty ?? 0;
        acc[email].confidence += chunk.confidence ?? 0;
        acc[email].count += 1;
        return acc;
      },
      {} as Record<string, { name: string; effort: number; complexity: number; novelty: number; confidence: number; count: number }>,
    );

  const totalEffort = Object.values(authorSummary).reduce((sum, a) => sum + a.effort, 0);

  // Build email -> color mapping
  const authorColorMap: Record<string, (typeof authorPalette)[0]> = {};
  Object.keys(authorSummary).forEach((email, idx) => {
    authorColorMap[email] = authorPalette[idx % authorPalette.length];
  });

  if (!chunks || chunks.length === 0) {
    return (
      <Card className="bg-muted/50">
        <CardContent className="py-8 text-center text-muted-foreground">
          No analysis data available. Recompute to see AI analysis details.
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      {/* Per-Person Average Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {Object.entries(authorSummary).map(([email, data]) => {
          const avgEffort = data.count > 0 ? data.effort / data.count : 0;
          const avgComplexity = data.count > 0 ? data.complexity / data.count : 0;
          const avgNovelty = data.count > 0 ? data.novelty / data.count : 0;

          return (
            <Card key={email} className={`border-l-4 ${authorColorMap[email]?.border ?? ''}`}>
              <CardHeader className="pb-2">
                <CardTitle className="text-lg">{data.name}</CardTitle>
                <p className="text-xs text-muted-foreground">{data.count} chunks analyzed</p>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-3 gap-3">
                  <div className="text-center p-3 bg-muted/30 rounded-lg">
                    <MetricLabel label="Avg. Effort" tooltip={metricInfo.effort} />
                    <p className="text-2xl font-bold" style={{ color: getEffortColor(avgEffort) }}>
                      {avgEffort.toFixed(1)}
                      <span className="text-sm text-muted-foreground font-normal">/10</span>
                    </p>
                  </div>
                  <div className="text-center p-3 bg-muted/30 rounded-lg">
                    <MetricLabel label="Avg. Complexity" tooltip={metricInfo.complexity} />
                    <p className="text-2xl font-bold">
                      {avgComplexity.toFixed(1)}
                      <span className="text-sm text-muted-foreground font-normal">/10</span>
                    </p>
                  </div>
                  <div className="text-center p-3 bg-muted/30 rounded-lg">
                    <MetricLabel label="Avg. Novelty" tooltip={metricInfo.novelty} />
                    <p className="text-2xl font-bold">
                      {avgNovelty.toFixed(1)}
                      <span className="text-sm text-muted-foreground font-normal">/10</span>
                    </p>
                  </div>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      {/* Effort Distribution - Stacked Bar */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-lg">Effort Distribution</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="w-full h-6 rounded-full overflow-hidden flex" style={{ backgroundColor: '#e5e7eb' }}>
            {Object.entries(authorSummary).map(([email, data]) => {
              const percentage = totalEffort > 0 ? (data.effort / totalEffort) * 100 : 0;
              return (
                <div
                  key={email}
                  style={{ width: `${percentage}%`, backgroundColor: authorColorMap[email]?.hex ?? '#9ca3af' }}
                  title={`${data.name}: ${Math.round(percentage)}%`}
                />
              );
            })}
          </div>
          <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2">
            {Object.entries(authorSummary).map(([email, data]) => {
              const percentage = totalEffort > 0 ? Math.round((data.effort / totalEffort) * 100) : 0;
              return (
                <div key={email} className="flex items-center gap-1.5 text-sm">
                  <span
                    className="h-2.5 w-2.5 rounded-full shrink-0"
                    style={{ backgroundColor: authorColorMap[email]?.hex ?? '#9ca3af' }}
                  />
                  <span className="font-medium">{data.name}</span>
                  <span className="text-muted-foreground">{percentage}%</span>
                </div>
              );
            })}
          </div>
        </CardContent>
      </Card>

      {/* Analysis Feed - Team Members */}
      <div className="space-y-3">
        <h3 className="text-lg font-semibold">Analysis History ({teamChunks.length} chunks)</h3>
        {teamChunks.map(chunk => {
          const chunkId = chunk.id ?? '';
          const isExpanded = expandedChunks.has(chunkId);
          const isError = chunk.isError;
          const badgeClass = isError ? '' : badgeColors[chunk.classification ?? 'TRIVIAL'] || badgeColors.TRIVIAL;
          const isLowEffort = !isError && (chunk.effortScore ?? 0) < 3.0;
          const cardStyle = isError
            ? {
                backgroundColor: '#fef2f2',
                borderTop: '1px solid #fecaca',
                borderRight: '1px solid #fecaca',
                borderBottom: '1px solid #fecaca',
              }
            : {
                backgroundColor: '#ffffff',
                borderTop: '1px solid #e5e7eb',
                borderRight: '1px solid #e5e7eb',
                borderBottom: '1px solid #e5e7eb',
              };

          return (
            <Card
              key={chunkId}
              className={`overflow-hidden transition-colors border-l-4 ${authorColorMap[chunk.authorEmail ?? 'unknown']?.border ?? ''} shadow-sm`}
              style={cardStyle}
            >
              <div className="flex items-center justify-between p-4 cursor-pointer hover:bg-muted/50" onClick={() => toggleExpand(chunkId)}>
                <div className="flex items-center gap-3 overflow-hidden">
                  {isError ? (
                    <Badge variant="destructive" className="shrink-0 flex items-center gap-1">
                      <AlertCircle className="w-3 h-3" /> FAILED
                    </Badge>
                  ) : (
                    <Badge className={`shrink-0 pointer-events-none ${badgeClass}`}>{chunk.classification}</Badge>
                  )}
                  <span className="font-medium truncate text-foreground">
                    {chunk.authorName ?? (chunk.authorEmail ?? 'unknown').split('@')[0]}
                  </span>
                  {chunk.isBundled && (
                    <Badge variant="outline" className="text-xs shrink-0 hidden sm:inline-flex">
                      Bundled
                    </Badge>
                  )}
                  {(chunk.totalChunks ?? 0) > 1 && (
                    <Badge variant="outline" className="text-xs shrink-0 hidden sm:inline-flex">
                      Part {(chunk.chunkIndex ?? 0) + 1}/{chunk.totalChunks}
                    </Badge>
                  )}
                </div>

                <div className="flex items-center gap-4">
                  {isLowEffort && <AlertTriangle className="h-4 w-4 text-red-500 shrink-0" />}
                  {!isError && (
                    <div className="hidden sm:flex items-center gap-2">
                      <span className="text-sm font-semibold" style={{ color: getEffortColor(chunk.effortScore ?? 0) }}>
                        {(chunk.effortScore ?? 0).toFixed(1)}
                      </span>
                      <span className="text-xs text-muted-foreground">effort</span>
                    </div>
                  )}
                  {isDevMode && (
                    <div className="hidden sm:flex items-center gap-2">
                      <span className="text-sm font-semibold text-foreground">
                        {(chunk.llmTokenUsage?.totalTokens ?? 0).toLocaleString()}
                      </span>
                      <span className="text-xs text-muted-foreground">tokens</span>
                    </div>
                  )}
                  <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
                    {isExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                  </Button>
                </div>
              </div>

              {isExpanded && (
                <CardContent className="border-t pt-4 space-y-3">
                  {isError ? (
                    <div className="space-y-2">
                      <p className="font-semibold text-red-600 dark:text-red-400 flex items-center gap-2">
                        <AlertCircle className="w-4 h-4" /> Error Details:
                      </p>
                      <div className="bg-background rounded p-3 border border-red-100 dark:border-red-900/30 font-mono text-xs overflow-x-auto whitespace-pre-wrap text-red-700 dark:text-red-300">
                        {chunk.errorMessage || 'Unknown error occurred during analysis.'}
                      </div>
                    </div>
                  ) : (
                    <>
                      {/* Effort breakdown */}
                      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                        <div className="text-center">
                          <MetricLabel label="Effort Score" tooltip={metricInfo.effort} />
                          <p className="text-lg font-bold" style={{ color: getEffortColor(chunk.effortScore ?? 0) }}>
                            {(chunk.effortScore ?? 0).toFixed(1)}/10
                          </p>
                        </div>
                        <div className="text-center">
                          <MetricLabel label="Complexity" tooltip={metricInfo.complexity} />
                          <p className="text-lg font-bold">{(chunk.complexity ?? 0).toFixed(1)}/10</p>
                        </div>
                        <div className="text-center">
                          <MetricLabel label="Novelty" tooltip={metricInfo.novelty} />
                          <p className="text-lg font-bold">{(chunk.novelty ?? 0).toFixed(1)}/10</p>
                        </div>
                        <div className="text-center">
                          <MetricLabel label="Confidence" tooltip={metricInfo.confidence} />
                          <p className="text-lg font-bold">{Math.round((chunk.confidence ?? 0) * 100)}%</p>
                        </div>
                      </div>

                      {isDevMode && (
                        <div className="p-3 bg-background rounded-lg border">
                          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-2">LLM Token Usage</p>
                          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                            <div>
                              <p className="text-xs text-muted-foreground mb-1">Prompt</p>
                              <p className="text-sm font-semibold">{(chunk.llmTokenUsage?.promptTokens ?? 0).toLocaleString()}</p>
                            </div>
                            <div>
                              <p className="text-xs text-muted-foreground mb-1">Completion</p>
                              <p className="text-sm font-semibold">{(chunk.llmTokenUsage?.completionTokens ?? 0).toLocaleString()}</p>
                            </div>
                            <div>
                              <p className="text-xs text-muted-foreground mb-1">Total</p>
                              <p className="text-sm font-semibold">{(chunk.llmTokenUsage?.totalTokens ?? 0).toLocaleString()}</p>
                            </div>
                            <div>
                              <p className="text-xs text-muted-foreground mb-1">Usage</p>
                              <p className="text-sm font-semibold">{chunk.llmTokenUsage?.usageAvailable ? 'Provided' : 'Unavailable'}</p>
                            </div>
                          </div>
                          {!chunk.llmTokenUsage?.usageAvailable && (
                            <p className="text-xs text-muted-foreground mt-2">Usage not provided by provider for this chunk.</p>
                          )}
                        </div>
                      )}

                      {/* AI Reasoning */}
                      <div>
                        <p className="text-sm font-medium text-muted-foreground mb-1">AI Reasoning:</p>
                        <p className="text-sm">{chunk.reasoning || 'No reasoning provided.'}</p>
                      </div>
                    </>
                  )}

                  {/* Commit details */}
                  <div className="space-y-2">
                    <p className="text-sm font-medium text-muted-foreground">Commits ({(chunk.commitShas ?? []).length}):</p>
                    {(chunk.commitShas ?? []).map((sha, idx) => (
                      <div key={sha} className="flex items-start gap-2 text-sm">
                        <GitCommit className="h-4 w-4 mt-0.5 text-muted-foreground shrink-0" />
                        <div className="min-w-0">
                          <code className="text-xs bg-muted px-1 py-0.5 rounded">{sha.slice(0, 7)}</code>
                          <span className="ml-2 break-all">{(chunk.commitMessages ?? [])[idx]}</span>
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Metadata */}
                  <div className="flex gap-4 text-xs text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <FileCode className="h-3 w-3" />
                      {chunk.linesChanged ?? 0} lines
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock className="h-3 w-3" />
                      {new Date(chunk.timestamp ?? new Date().toISOString()).toLocaleDateString()}
                    </span>
                  </div>
                </CardContent>
              )}
            </Card>
          );
        })}
      </div>

      {/* External Contributors Section */}
      {externalChunks.length > 0 && (
        <Collapsible open={externalOpen} onOpenChange={setExternalOpen}>
          <Card className="border-amber-200 dark:border-amber-800/50 bg-amber-50/30 dark:bg-amber-900/10">
            <CollapsibleTrigger asChild>
              <CardHeader className="cursor-pointer hover:bg-amber-100/50 dark:hover:bg-amber-900/20 transition-colors">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <UserX className="h-5 w-5 text-amber-600 dark:text-amber-400" />
                    <CardTitle className="text-lg text-amber-700 dark:text-amber-300">
                      External Contributions ({externalChunks.length} chunks)
                    </CardTitle>
                  </div>
                  <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
                    {externalOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                  </Button>
                </div>
                <p className="text-sm text-amber-600/80 dark:text-amber-400/80 mt-1">
                  These commits are from contributors not registered as team members. They are shown for transparency but are{' '}
                  <strong>not included</strong> in the CQI calculation.
                </p>
              </CardHeader>
            </CollapsibleTrigger>
            <CollapsibleContent>
              <CardContent className="space-y-3 pt-0">
                {externalChunks.map(chunk => {
                  const chunkId = `ext-${chunk.id ?? ''}`;
                  const isExpanded = expandedChunks.has(chunkId);
                  const badgeClass = badgeColors[chunk.classification ?? 'TRIVIAL'] || badgeColors.TRIVIAL;

                  return (
                    <Card
                      key={chunkId}
                      className={`overflow-hidden transition-colors border-amber-200/50 bg-white dark:bg-background border border-gray-200 dark:border-gray-700`}
                    >
                      <div
                        className="flex items-center justify-between p-4 cursor-pointer hover:bg-muted/50"
                        onClick={() => toggleExpand(chunkId)}
                      >
                        <div className="flex items-center gap-3 overflow-hidden">
                          <Badge className={`shrink-0 ${badgeClass}`}>{chunk.classification}</Badge>
                          <Badge variant="outline" className="shrink-0 text-amber-600 border-amber-300">
                            <UserX className="w-3 h-3 mr-1" /> External
                          </Badge>
                          <span className="font-medium truncate">{chunk.authorName ?? (chunk.authorEmail ?? 'unknown').split('@')[0]}</span>
                        </div>

                        <div className="flex items-center gap-4">
                          <div className="hidden sm:flex items-center gap-2">
                            <span className="text-sm font-semibold text-muted-foreground">{(chunk.effortScore ?? 0).toFixed(1)}</span>
                            <span className="text-xs text-muted-foreground">effort</span>
                          </div>
                          {isDevMode && (
                            <div className="hidden sm:flex items-center gap-2">
                              <span className="text-sm font-semibold text-foreground">
                                {(chunk.llmTokenUsage?.totalTokens ?? 0).toLocaleString()}
                              </span>
                              <span className="text-xs text-muted-foreground">tokens</span>
                            </div>
                          )}
                          <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
                            {isExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                          </Button>
                        </div>
                      </div>

                      {isExpanded && (
                        <CardContent className="border-t bg-muted/30 pt-4 space-y-3">
                          {isDevMode && (
                            <div className="p-3 bg-background rounded-lg border">
                              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-2">LLM Token Usage</p>
                              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                                <div>
                                  <p className="text-xs text-muted-foreground mb-1">Prompt</p>
                                  <p className="text-sm font-semibold">{(chunk.llmTokenUsage?.promptTokens ?? 0).toLocaleString()}</p>
                                </div>
                                <div>
                                  <p className="text-xs text-muted-foreground mb-1">Completion</p>
                                  <p className="text-sm font-semibold">{(chunk.llmTokenUsage?.completionTokens ?? 0).toLocaleString()}</p>
                                </div>
                                <div>
                                  <p className="text-xs text-muted-foreground mb-1">Total</p>
                                  <p className="text-sm font-semibold">{(chunk.llmTokenUsage?.totalTokens ?? 0).toLocaleString()}</p>
                                </div>
                                <div>
                                  <p className="text-xs text-muted-foreground mb-1">Usage</p>
                                  <p className="text-sm font-semibold">
                                    {chunk.llmTokenUsage?.usageAvailable ? 'Provided' : 'Unavailable'}
                                  </p>
                                </div>
                              </div>
                              {!chunk.llmTokenUsage?.usageAvailable && (
                                <p className="text-xs text-muted-foreground mt-2">Usage not provided by provider for this chunk.</p>
                              )}
                            </div>
                          )}

                          {/* AI Reasoning */}
                          <div>
                            <p className="text-sm font-medium text-muted-foreground mb-1">AI Reasoning:</p>
                            <p className="text-sm">{chunk.reasoning || 'No reasoning provided.'}</p>
                          </div>

                          {/* Commit details */}
                          <div className="space-y-2">
                            <p className="text-sm font-medium text-muted-foreground">Commits ({(chunk.commitShas ?? []).length}):</p>
                            {(chunk.commitShas ?? []).map((sha, idx) => (
                              <div key={sha} className="flex items-start gap-2 text-sm">
                                <GitCommit className="h-4 w-4 mt-0.5 text-muted-foreground shrink-0" />
                                <div className="min-w-0">
                                  <code className="text-xs bg-muted px-1 py-0.5 rounded">{sha.slice(0, 7)}</code>
                                  <span className="ml-2 break-all">{(chunk.commitMessages ?? [])[idx]}</span>
                                </div>
                              </div>
                            ))}
                          </div>

                          {/* Metadata */}
                          <div className="flex gap-4 text-xs text-muted-foreground">
                            <span className="flex items-center gap-1">
                              <FileCode className="h-3 w-3" />
                              {chunk.linesChanged ?? 0} lines
                            </span>
                            <span className="flex items-center gap-1">
                              <Clock className="h-3 w-3" />
                              {new Date(chunk.timestamp ?? new Date().toISOString()).toLocaleDateString()}
                            </span>
                          </div>
                        </CardContent>
                      )}
                    </Card>
                  );
                })}
              </CardContent>
            </CollapsibleContent>
          </Card>
        </Collapsible>
      )}
    </div>
  );
};

export default AnalysisFeed;

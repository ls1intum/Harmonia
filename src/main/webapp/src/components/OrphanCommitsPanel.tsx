import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ChevronDown, ChevronUp, AlertTriangle, GitCommit, User, FileCode } from 'lucide-react';
import type { OrphanCommitDTO } from '@/app/generated';
import { Badge } from '@/components/ui/badge';

interface OrphanCommitsPanelProps {
  commits: OrphanCommitDTO[];
}

const OrphanCommitsPanel = ({ commits }: OrphanCommitsPanelProps) => {
  const [isOpen, setIsOpen] = useState(false);

  if (!commits || commits.length === 0) {
    return null;
  }

  const totalLines = commits.reduce((sum, c) => sum + (c.linesAdded ?? 0) + (c.linesDeleted ?? 0), 0);

  return (
    <Card className="border-amber-200 bg-amber-50/50 dark:bg-amber-950/10 mb-6">
      <CardHeader
        className="py-3 px-4 cursor-pointer hover:bg-amber-100/50 dark:hover:bg-amber-900/20 transition-colors"
        onClick={() => setIsOpen(!isOpen)}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-400" />
            <CardTitle className="text-base font-medium text-amber-900 dark:text-amber-200">
              {commits.length} Commit{commits.length !== 1 ? 's' : ''} Could Not Be Attributed
            </CardTitle>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-amber-700 dark:text-amber-300">{totalLines} lines</span>
            <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
              {isOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
            </Button>
          </div>
        </div>
      </CardHeader>

      {isOpen && (
        <CardContent className="px-4 pb-4 pt-0">
          <p className="text-sm text-muted-foreground mb-4">
            These commits were made with email addresses that don't match any registered team member. The student may have used a different
            email for Git configuration.
          </p>
          <div className="h-[300px] w-full overflow-y-auto pr-2">
            <div className="space-y-2">
              {commits.map(commit => (
                <div key={commit.commitHash ?? ''} className="bg-background rounded-lg border border-amber-100 dark:border-amber-900/50 p-3">
                  <div className="flex items-start gap-3">
                    <GitCommit className="w-4 h-4 mt-1 text-muted-foreground shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap mb-1">
                        <code className="text-xs bg-muted px-1.5 py-0.5 rounded font-mono">{(commit.commitHash ?? '').substring(0, 7)}</code>
                        <Badge variant="outline" className="text-xs gap-1">
                          <User className="w-3 h-3" />
                          {commit.authorEmail ?? 'unknown'}
                        </Badge>
                        <span className="text-xs text-muted-foreground ml-auto">{new Date(commit.timestamp ?? new Date().toISOString()).toLocaleString()}</span>
                      </div>
                      <p className="text-sm font-medium truncate mb-1">{commit.message ?? ''}</p>
                      <div className="flex items-center gap-3 text-xs text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <FileCode className="w-3 h-3" />
                          <span className="text-green-600">+{commit.linesAdded ?? 0}</span>
                          {' / '}
                          <span className="text-red-600">-{commit.linesDeleted ?? 0}</span>
                        </span>
                        {commit.authorName && <span>by {commit.authorName}</span>}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </CardContent>
      )}
    </Card>
  );
};

export default OrphanCommitsPanel;

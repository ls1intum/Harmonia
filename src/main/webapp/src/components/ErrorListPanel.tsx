import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ChevronDown, ChevronUp, AlertCircle, Terminal } from 'lucide-react';
import type { AnalysisError } from '@/types/team';
import { Badge } from '@/components/ui/badge';

interface ErrorListPanelProps {
  errors: AnalysisError[];
}

const ErrorListPanel = ({ errors }: ErrorListPanelProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const [expandedErrorId, setExpandedErrorId] = useState<string | null>(null);

  if (!errors || errors.length === 0) {
    return null;
  }

  const toggleError = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setExpandedErrorId(prev => (prev === id ? null : id));
  };

  return (
    <Card className="border-red-200 bg-red-50/50 dark:bg-red-950/10 mb-6">
      <CardHeader
        className="py-3 px-4 cursor-pointer hover:bg-red-100/50 dark:hover:bg-red-900/20 transition-colors"
        onClick={() => setIsOpen(!isOpen)}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-5 h-5 text-red-600 dark:text-red-400" />
            <CardTitle className="text-base font-medium text-red-900 dark:text-red-200">
              Analysis Failed for {errors.length} Chunk{errors.length !== 1 ? 's' : ''}
            </CardTitle>
          </div>
          <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
            {isOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
          </Button>
        </div>
      </CardHeader>

      {isOpen && (
        <CardContent className="px-4 pb-4 pt-0">
          <div className="h-[300px] w-full pr-4 mt-2 overflow-y-auto custom-scrollbar">
            <div className="space-y-3">
              {errors.map(error => (
                <div
                  key={error.id}
                  className="bg-background rounded-lg border border-red-100 dark:border-red-900/50 shadow-sm overflow-hidden"
                >
                  <div
                    className="p-3 flex items-start gap-3 cursor-pointer hover:bg-muted/50 transition-colors"
                    onClick={e => toggleError(error.id, e)}
                  >
                    <div className="mt-1">
                      <Terminal className="w-4 h-4 text-muted-foreground" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1 flex-wrap">
                        <span className="font-medium text-sm truncate">{error.authorEmail}</span>
                        <Badge variant="outline" className="text-xs font-mono">
                          {error.commitShas[0]?.substring(0, 7)}
                          {error.commitShas.length > 1 && ` +${error.commitShas.length - 1}`}
                        </Badge>
                        <span className="text-xs text-muted-foreground ml-auto">{new Date(error.timestamp).toLocaleTimeString()}</span>
                      </div>
                      <p className="text-sm text-red-600 dark:text-red-400 font-medium truncate">{error.errorMessage.split('\n')[0]}</p>
                    </div>
                    <Button variant="ghost" size="sm" className="h-6 w-6 p-0 shrink-0">
                      {expandedErrorId === error.id ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
                    </Button>
                  </div>

                  {expandedErrorId === error.id && (
                    <div className="p-3 pt-0 border-t bg-muted/30">
                      <div className="rounded bg-muted p-2 mt-3 font-mono text-xs overflow-x-auto whitespace-pre-wrap break-words">
                        {error.errorMessage}
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </CardContent>
      )}
    </Card>
  );
};

export default ErrorListPanel;

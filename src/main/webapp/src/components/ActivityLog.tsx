import { useRef, useEffect } from 'react';
import { Card } from '@/components/ui/card';
import { Terminal } from 'lucide-react';

export interface LogEntry {
  team: string;
  message: string;
  timestamp: Date;
}

interface ActivityLogProps {
  logs: LogEntry[];
  maxHeight?: number;
}

export function ActivityLog({ logs, maxHeight = 200 }: ActivityLogProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom when new logs arrive
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [logs]);

  if (logs.length === 0) {
    return null;
  }

  return (
    <Card className="bg-zinc-950 border-zinc-800 overflow-hidden">
      <div className="flex items-center gap-2 px-4 py-2 border-b border-zinc-800 bg-zinc-900">
        <Terminal className="h-4 w-4 text-zinc-400" />
        <span className="text-sm font-medium text-zinc-300">Activity Log</span>
        <span className="text-xs text-zinc-500 ml-auto">{logs.length} events</span>
      </div>
      <div className="p-4 overflow-y-auto" style={{ maxHeight }} ref={scrollRef}>
        <div className="space-y-1 font-mono text-xs">
          {logs.map((log, index) => (
            <div key={index} className="flex gap-2">
              <span className="text-zinc-500 shrink-0">{log.timestamp.toLocaleTimeString()}</span>
              <span className="text-cyan-400 shrink-0 min-w-[100px]">[{log.team}]</span>
              <span className={getMessageColor(log.message)}>{log.message}</span>
            </div>
          ))}
        </div>
      </div>
    </Card>
  );
}

function getMessageColor(message: string): string {
  if (message.startsWith('✓')) return 'text-green-400';
  if (message.startsWith('✗')) return 'text-red-400';
  if (message.includes('AI')) return 'text-purple-400';
  return 'text-zinc-300';
}

export default ActivityLog;

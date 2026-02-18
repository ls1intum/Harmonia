import { Badge } from '@/components/ui/badge';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import type { PairProgrammingBadgeStatus } from '@/lib/pairProgramming';

interface PairProgrammingBadgeProps {
  status: PairProgrammingBadgeStatus | null;
}

const PairProgrammingBadge = ({ status }: PairProgrammingBadgeProps) => {
  if (!status) {
    return null;
  }

  if (status === 'warning') {
    return (
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <Badge variant="outline" className="gap-1.5 cursor-help text-warning border-warning/50 bg-warning/10">
              Warning
            </Badge>
          </TooltipTrigger>
          <TooltipContent>
            <p>Team not found. Verify the name matches Excel exactly.</p>
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
  }

  if (status === 'pass') {
    return (
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <Badge variant="secondary" className="gap-1.5 cursor-help bg-success/10 text-success hover:bg-success/20">
              Pass
            </Badge>
          </TooltipTrigger>
          <TooltipContent className="max-w-xs">
            <p>Team was found in Excel and attended at least 2 of 3 pair-programming sessions.</p>
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
  }

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Badge variant="destructive" className="gap-1.5 cursor-help">
            Fail
          </Badge>
        </TooltipTrigger>
        <TooltipContent className="max-w-xs">
          <p>Team was found in Excel but attended fewer than 2 of 3 pair-programming sessions.</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
};

export default PairProgrammingBadge;

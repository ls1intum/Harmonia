import { Filter } from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
} from '@/components/ui/dropdown-menu';
import { Button } from '@/components/ui/button';
import type { PairProgrammingBadgeStatus } from '@/lib/pairProgramming';
import { PAIR_PROGRAMMING_BADGE_STATUSES, PAIR_PROGRAMMING_BADGE_STATUS_LABELS } from '@/lib/pairProgramming';

export type PairProgrammingFilter = 'all' | PairProgrammingBadgeStatus;

interface PairProgrammingFilterButtonProps {
  pairProgrammingFilter: PairProgrammingFilter;
  setPairProgrammingFilter: (value: PairProgrammingFilter) => void;
}

export const PairProgrammingFilterButton = ({ pairProgrammingFilter, setPairProgrammingFilter }: PairProgrammingFilterButtonProps) => {
  const isActive = pairProgrammingFilter !== 'all';

  return (
    <div className="flex items-center gap-2">
      Pair Programming
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" className={`h-6 w-6 ${isActive ? 'bg-primary/10 text-primary' : ''}`}>
            <Filter className="h-3.5 w-3.5" />
          </Button>
        </DropdownMenuTrigger>

        <DropdownMenuContent align="end" className="bg-popover">
          <DropdownMenuLabel>Filter by</DropdownMenuLabel>
          <DropdownMenuSeparator />

          <DropdownMenuRadioGroup
            value={pairProgrammingFilter}
            onValueChange={value => setPairProgrammingFilter(value as PairProgrammingFilter)}
          >
            <DropdownMenuRadioItem value="all">All</DropdownMenuRadioItem>
            {PAIR_PROGRAMMING_BADGE_STATUSES.map(status => (
              <DropdownMenuRadioItem key={status} value={status}>
                {PAIR_PROGRAMMING_BADGE_STATUS_LABELS[status]}
              </DropdownMenuRadioItem>
            ))}
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
};

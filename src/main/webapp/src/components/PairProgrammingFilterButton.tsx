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

export type PairProgrammingFilter = 'all' | 'pass' | 'fail' | 'warning' | 'not_found';

interface PairProgrammingFilterButtonProps {
  filter: PairProgrammingFilter;
  setFilter: (value: PairProgrammingFilter) => void;
}

/**
 * Dropdown filter for pair-programming attendance status.
 *
 * @param props.filter - currently active filter value
 * @param props.setFilter - callback to change the filter
 */
export const PairProgrammingFilterButton = ({ filter, setFilter }: PairProgrammingFilterButtonProps) => {
  const isActive = filter !== 'all';

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

          <DropdownMenuRadioGroup value={filter} onValueChange={(value) => setFilter(value as PairProgrammingFilter)}>
            <DropdownMenuRadioItem value="all">All</DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="pass">Pass</DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="fail">Fail</DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="warning">Warning</DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="not_found">Not Found</DropdownMenuRadioItem>
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
};

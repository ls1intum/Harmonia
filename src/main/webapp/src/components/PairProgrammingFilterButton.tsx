import { Filter } from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuCheckboxItem,
} from '@/components/ui/dropdown-menu';
import { Button } from '@/components/ui/button';

export type PairProgrammingFilterValue = 'pass' | 'fail' | 'warning' | 'not_found';

interface PairProgrammingFilterButtonProps {
  filter: PairProgrammingFilterValue[];
  setFilter: (value: PairProgrammingFilterValue[]) => void;
}

/**
 * Dropdown multi-select filter for pair-programming attendance status.
 *
 * @param filter - currently active filter values
 * @param setFilter - callback to change the filter
 */
export const PairProgrammingFilterButton = ({ filter, setFilter }: PairProgrammingFilterButtonProps) => {
  const count = filter.length;
  const isActive = count > 0;

  const toggle = (value: PairProgrammingFilterValue) => {
    if (filter.includes(value)) {
      setFilter(filter.filter(v => v !== value));
    } else {
      setFilter(filter.concat(value));
    }
  };

  return (
    <div className="flex items-center gap-2">
      Pair Programming
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" className={`relative h-6 w-6 ${isActive ? 'bg-primary/10 text-primary' : ''}`}>
            <Filter className={`h-3.5 w-3.5 ${isActive ? 'fill-primary' : ''}`} />
            {isActive && (
              <span className="absolute -top-1 -right-1 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-primary text-[9px] font-bold text-primary-foreground">
                {count}
              </span>
            )}
          </Button>
        </DropdownMenuTrigger>

        <DropdownMenuContent align="end" className="bg-popover">
          <DropdownMenuLabel>Filter by</DropdownMenuLabel>
          <DropdownMenuSeparator />

          <DropdownMenuCheckboxItem checked={filter.includes('pass')} onCheckedChange={() => toggle('pass')}>
            Pass
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem checked={filter.includes('fail')} onCheckedChange={() => toggle('fail')}>
            Fail
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem checked={filter.includes('warning')} onCheckedChange={() => toggle('warning')}>
            Warning
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem checked={filter.includes('not_found')} onCheckedChange={() => toggle('not_found')}>
            Not Found
          </DropdownMenuCheckboxItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
};

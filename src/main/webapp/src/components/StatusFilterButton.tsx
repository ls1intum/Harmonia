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

export type StatusFilterValue =
  | 'normal'
  | 'suspicious'
  | 'failed'
  | 'git-done'
  | 'error'
  | 'has-unmatched'
  | 'no-unmatched'
  | 'reviewed'
  | 'unreviewed';

interface StatusFilterButtonProps {
  statusFilter: StatusFilterValue[];
  setStatusFilter: (value: StatusFilterValue[]) => void;
}

/**
 * Dropdown multi-select filter for team status.
 *
 * @param props.statusFilter - currently active filter values
 * @param props.setStatusFilter - callback to change the filter
 */
export const StatusFilterButton = ({ statusFilter, setStatusFilter }: StatusFilterButtonProps) => {
  const count = statusFilter.length;
  const isActive = count > 0;

  const toggle = (value: StatusFilterValue) => {
    if (statusFilter.includes(value)) {
      setStatusFilter(statusFilter.filter(v => v !== value));
    } else {
      setStatusFilter(statusFilter.concat(value));
    }
  };

  return (
    <div className="flex items-center gap-2">
      Status
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

          <DropdownMenuCheckboxItem checked={statusFilter.includes('normal')} onCheckedChange={() => toggle('normal')}>
            Normal
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem checked={statusFilter.includes('suspicious')} onCheckedChange={() => toggle('suspicious')}>
            Suspicious
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem checked={statusFilter.includes('failed')} onCheckedChange={() => toggle('failed')}>
            Failed
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem checked={statusFilter.includes('git-done')} onCheckedChange={() => toggle('git-done')}>
            Git Done
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem checked={statusFilter.includes('error')} onCheckedChange={() => toggle('error')}>
            Error
          </DropdownMenuCheckboxItem>
          <DropdownMenuSeparator />
          <DropdownMenuCheckboxItem checked={statusFilter.includes('has-unmatched')} onCheckedChange={() => toggle('has-unmatched')}>
            Has Unmatched
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem checked={statusFilter.includes('no-unmatched')} onCheckedChange={() => toggle('no-unmatched')}>
            No Unmatched
          </DropdownMenuCheckboxItem>
          <DropdownMenuSeparator />
          <DropdownMenuCheckboxItem checked={statusFilter.includes('reviewed')} onCheckedChange={() => toggle('reviewed')}>
            Reviewed
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem checked={statusFilter.includes('unreviewed')} onCheckedChange={() => toggle('unreviewed')}>
            Unreviewed
          </DropdownMenuCheckboxItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
};

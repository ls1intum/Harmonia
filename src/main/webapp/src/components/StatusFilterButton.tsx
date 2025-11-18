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

export type StatusFilter = 'all' | 'normal' | 'suspicious';

interface StatusFilterButtonProps {
  statusFilter: StatusFilter;
  setStatusFilter: (value: StatusFilter) => void;
}

export const StatusFilterButton = ({ statusFilter, setStatusFilter }: StatusFilterButtonProps) => {
  const isActive = statusFilter !== 'all';

  return (
    <div className="flex items-center gap-2">
      Status
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" className={`h-6 w-6 ${isActive ? 'bg-primary/10 text-primary' : ''}`}>
            <Filter className="h-3.5 w-3.5" />
          </Button>
        </DropdownMenuTrigger>

        <DropdownMenuContent align="end" className="bg-popover">
          <DropdownMenuLabel>Filter by</DropdownMenuLabel>
          <DropdownMenuSeparator />

          <DropdownMenuRadioGroup value={statusFilter} onValueChange={value => setStatusFilter(value as StatusFilter)}>
            <DropdownMenuRadioItem value="all">All</DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="normal">Normal</DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="suspicious">Suspicious</DropdownMenuRadioItem>
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
};

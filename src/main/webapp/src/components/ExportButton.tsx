import { Download, ChevronDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';

interface ExportButtonProps {
  exerciseId: string;
  disabled?: boolean;
}

type ExportFormat = 'EXCEL' | 'JSON';

function triggerDownload(exerciseId: string, format: ExportFormat) {
  const params = new URLSearchParams();
  params.set('format', format);
  params.append('include', 'teams');
  params.append('include', 'students');
  params.append('include', 'chunks');
  params.append('include', 'commits');
  const url = `/api/export/${exerciseId}?${params.toString()}`;
  const a = document.createElement('a');
  a.href = url;
  a.download = '';
  document.body.appendChild(a);
  a.click();
  a.remove();
}

const ExportButton = ({ exerciseId, disabled = false }: ExportButtonProps) => {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" disabled={disabled}>
          <Download className="h-4 w-4" />
          Export
          <ChevronDown className="h-3 w-3 ml-1" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={() => triggerDownload(exerciseId, 'EXCEL')}>Excel (.xlsx)</DropdownMenuItem>
        <DropdownMenuItem onClick={() => triggerDownload(exerciseId, 'JSON')}>JSON</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export default ExportButton;

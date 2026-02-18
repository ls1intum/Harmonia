import { useState } from 'react';
import { Download, ChevronDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

interface ExportButtonProps {
  exerciseId: string;
  disabled?: boolean;
}

type ExportFormat = 'CSV' | 'EXCEL' | 'JSON';
type DataScope = 'teams' | 'students' | 'chunks' | 'commits';

const DATA_SCOPES: { value: DataScope; label: string }[] = [
  { value: 'teams', label: 'Teams' },
  { value: 'students', label: 'Students' },
  { value: 'chunks', label: 'Analyzed Chunks' },
  { value: 'commits', label: 'Commits' },
];

function triggerDownload(exerciseId: string, format: ExportFormat, include: DataScope[]) {
  const params = new URLSearchParams();
  params.set('format', format);
  for (const scope of include) {
    params.append('include', scope);
  }
  const url = `/api/export/${exerciseId}?${params.toString()}`;
  const a = document.createElement('a');
  a.href = url;
  a.download = '';
  document.body.appendChild(a);
  a.click();
  a.remove();
}

const ExportButton = ({ exerciseId, disabled = false }: ExportButtonProps) => {
  const [customDialogOpen, setCustomDialogOpen] = useState(false);
  const [customFormat, setCustomFormat] = useState<ExportFormat>('CSV');
  const [customScopes, setCustomScopes] = useState<Set<DataScope>>(new Set(['teams', 'students']));

  const toggleScope = (scope: DataScope) => {
    setCustomScopes(prev => {
      const next = new Set(prev);
      if (next.has(scope)) {
        next.delete(scope);
      } else {
        next.add(scope);
      }
      return next;
    });
  };

  const handleQuickExport = (format: ExportFormat) => {
    triggerDownload(exerciseId, format, ['teams', 'students']);
  };

  const handleCustomExport = () => {
    if (customScopes.size === 0) return;
    triggerDownload(exerciseId, customFormat, Array.from(customScopes));
    setCustomDialogOpen(false);
  };

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" disabled={disabled}>
            <Download className="h-4 w-4" />
            Export
            <ChevronDown className="h-3 w-3 ml-1" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onClick={() => handleQuickExport('CSV')}>CSV</DropdownMenuItem>
          <DropdownMenuItem onClick={() => handleQuickExport('EXCEL')}>Excel (.xlsx)</DropdownMenuItem>
          <DropdownMenuItem onClick={() => handleQuickExport('JSON')}>JSON</DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => setCustomDialogOpen(true)}>Custom Export...</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <AlertDialog open={customDialogOpen} onOpenChange={setCustomDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Custom Export</AlertDialogTitle>
            <AlertDialogDescription>Choose the format and data to include in the export.</AlertDialogDescription>
          </AlertDialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>Format</Label>
              <Select value={customFormat} onValueChange={v => setCustomFormat(v as ExportFormat)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CSV">CSV</SelectItem>
                  <SelectItem value="EXCEL">Excel (.xlsx)</SelectItem>
                  <SelectItem value="JSON">JSON</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>Data to include</Label>
              <div className="space-y-2">
                {DATA_SCOPES.map(scope => (
                  <label key={scope.value} className="flex items-center gap-2 text-sm cursor-pointer">
                    <input
                      type="checkbox"
                      checked={customScopes.has(scope.value)}
                      onChange={() => toggleScope(scope.value)}
                      className="h-4 w-4 rounded border-input"
                    />
                    {scope.label}
                  </label>
                ))}
              </div>
            </div>
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleCustomExport} disabled={customScopes.size === 0}>
              Export
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
};

export default ExportButton;

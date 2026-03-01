import { Download, ChevronDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { toast } from '@/hooks/use-toast';
import { ExportDataFormatEnum } from '@/app/generated';
import { exportApi } from '@/lib/apiClient';
import { useMutation } from '@tanstack/react-query';

interface ExportButtonProps {
  exerciseId: string;
  disabled?: boolean;
}

/**
 * Dropdown button that exports analysis results as Excel or JSON.
 *
 * @param props.exerciseId - exercise whose data is exported
 * @param props.disabled - disables the button while analysis is in progress
 */
const ExportButton = ({ exerciseId, disabled = false }: ExportButtonProps) => {
  const exportMutation = useMutation({
    mutationFn: async (format: (typeof ExportDataFormatEnum)[keyof typeof ExportDataFormatEnum]) => {
      const include = ['teams', 'students', 'chunks', 'commits'];
      const response = await exportApi.exportData(Number(exerciseId), format, include, {
        responseType: 'blob',
      });
      const blob = new Blob([response.data]);
      const disposition = response.headers?.['content-disposition'];
      const filename =
        disposition?.match(/filename="(.+)"/)?.[1] ?? `export-${exerciseId}.${format === ExportDataFormatEnum.Excel ? 'xlsx' : 'json'}`;
      const objectUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = objectUrl;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(objectUrl);
    },
    onError: () => {
      toast.error('Export failed', 'Could not download export file');
    },
  });

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" disabled={disabled || exportMutation.isPending}>
          <Download className="h-4 w-4" />
          {exportMutation.isPending ? 'Exporting...' : 'Export'}
          <ChevronDown className="h-3 w-3 ml-1" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={() => exportMutation.mutate(ExportDataFormatEnum.Excel)}>Excel (.xlsx)</DropdownMenuItem>
        <DropdownMenuItem onClick={() => exportMutation.mutate(ExportDataFormatEnum.Json)}>JSON</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export default ExportButton;

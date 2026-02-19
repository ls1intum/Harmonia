import { Download, ChevronDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { toast } from '@/hooks/use-toast';
import { ExportResourceApi, Configuration, ExportDataFormatEnum } from '@/app/generated';

interface ExportButtonProps {
  exerciseId: string;
  disabled?: boolean;
}

const apiConfig = new Configuration({
  basePath: window.location.origin,
  baseOptions: {
    withCredentials: true,
  },
});
const exportApi = new ExportResourceApi(apiConfig);

async function triggerDownload(exerciseId: string, format: typeof ExportDataFormatEnum[keyof typeof ExportDataFormatEnum]) {
  const include = ['teams', 'students', 'chunks', 'commits'];

  try {
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
  } catch {
    toast.error('Export failed', 'Could not download export file');
  }
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
        <DropdownMenuItem onClick={() => triggerDownload(exerciseId, ExportDataFormatEnum.Excel)}>Excel (.xlsx)</DropdownMenuItem>
        <DropdownMenuItem onClick={() => triggerDownload(exerciseId, ExportDataFormatEnum.Json)}>JSON</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export default ExportButton;

import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { FileSpreadsheet, X } from 'lucide-react';
import { useState } from 'react';
import { Button } from '@/components/ui/button';

interface FileUploadProps {
  onFileSelect: (file: File | null) => void;
  disabled?: boolean;
}

export default function FileUpload({ onFileSelect, disabled }: FileUploadProps) {
  const [error, setError] = useState('');
  const [selectedFileName, setSelectedFileName] = useState<string | null>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const fileExtension = file.name.split('.').pop()?.toLowerCase();
      if (fileExtension !== 'xlsx') {
        setError('Only XLSX files are allowed');
        setSelectedFileName(null);
        onFileSelect(null);
        e.target.value = '';
        return;
      }

      setError('');
      setSelectedFileName(file.name);
      onFileSelect(file);
    } else {
      setSelectedFileName(null);
      onFileSelect(null);
    }
  };

  const handleClear = () => {
    setSelectedFileName(null);
    setError('');
    onFileSelect(null);
    // Reset the input
    const input = document.getElementById('attendance-file') as HTMLInputElement;
    if (input) {
      input.value = '';
    }
  };

  return (
    <div className="space-y-2">
      <Label htmlFor="attendance-file" className="flex items-center gap-2">
        <FileSpreadsheet className="h-4 w-4" />
        Pair Programming Attendance (optional)
      </Label>
      <div className="flex gap-2">
        <Input
          id="attendance-file"
          name="attendance-file"
          type="file"
          accept=".xlsx"
          className="pt-2 flex-1"
          onChange={handleFileChange}
          disabled={disabled}
        />
        {selectedFileName && (
          <Button type="button" variant="ghost" size="icon" onClick={handleClear} disabled={disabled}>
            <X className="h-4 w-4" />
          </Button>
        )}
      </div>
      {selectedFileName && <p className="text-sm text-muted-foreground">Selected: {selectedFileName}</p>}
      {error && <p className="text-sm text-destructive">{error}</p>}
      <p className="text-sm text-muted-foreground">Upload an XLSX file with pair programming attendance data.</p>
    </div>
  );
}

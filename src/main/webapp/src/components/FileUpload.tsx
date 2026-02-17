import { Input } from '@/components/ui/input';
import { X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';

interface FileUploadProps {
  file: File | null;
  onFileSelect: (file: File | null) => void;
  disabled?: boolean;
  inputId?: string;
  label?: string;
  helperText?: string;
}

export default function FileUpload({
  file,
  onFileSelect,
  disabled,
  inputId = 'attendance-file',
}: FileUploadProps) {
  const [error, setError] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!file && inputRef.current) {
      inputRef.current.value = '';
    }
  }, [file]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (selectedFile) {
      const fileExtension = selectedFile.name.split('.').pop()?.toLowerCase();
      if (fileExtension !== 'xlsx') {
        setError('Only XLSX files are allowed');
        onFileSelect(null);
        e.target.value = '';
        return;
      }

      setError('');
      onFileSelect(selectedFile);
    } else {
      onFileSelect(null);
    }
  };

  const handleClear = () => {
    setError('');
    onFileSelect(null);
    if (inputRef.current) {
      inputRef.current.value = '';
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <Input
          ref={inputRef}
          id={inputId}
          name={inputId}
          type="file"
          accept=".xlsx"
          className="pt-2 flex-1"
          onChange={handleFileChange}
          disabled={disabled}
        />
        {file && (
          <Button type="button" variant="ghost" size="icon" onClick={handleClear} disabled={disabled}>
            <X className="h-4 w-4" />
          </Button>
        )}
      </div>
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  );
}

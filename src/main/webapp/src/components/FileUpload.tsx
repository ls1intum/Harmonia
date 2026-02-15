// import { Button } from "@/components/ui/button";
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { FileSpreadsheet, Loader2 } from 'lucide-react';
import { useState } from 'react';
import { toast } from '@/hooks/use-toast';

interface FileUploadProps {
  courseId?: string;
  exerciseId?: string;
  serverUrl?: string;
  username?: string;
  password?: string;
}

export default function FileUpload({ courseId, exerciseId, serverUrl, username, password }: FileUploadProps) {
  const [error, setError] = useState('');
  const [isUploading, setIsUploading] = useState(false);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const fileExtension = file.name.split('.').pop()?.toLowerCase();
      if (fileExtension !== 'xlsx') {
        setError('Only XLSX files are allowed');
        e.target.value = '';
        return;
      }

      if (!courseId) {
        setError('Please enter a valid exercise URL before uploading.');
        e.target.value = '';
        return;
      }
      if (!serverUrl || !username || !password) {
        setError('Please enter exercise URL, username, and password before uploading.');
        e.target.value = '';
        return;
      }

      setError('');

      const formData = new FormData();
      formData.append('file', file);
      if (serverUrl) {
        formData.append('serverUrl', serverUrl);
      }
      if (username) {
        formData.append('username', username);
      }
      if (password) {
        formData.append('password', password);
      }

      setIsUploading(true);
      try {
        const response = await fetch(`/api/attendance/upload?courseId=${courseId}&exerciseId=${exerciseId}`, {
          method: 'POST',
          body: formData,
        });

        if (!response.ok) {
          throw new Error('Upload failed');
        }

        toast({
          title: 'Attendance uploaded',
          description: 'Attendance data was parsed and combined with tutorial group sessions.',
        });
      } catch {
        toast({
          variant: 'destructive',
          title: 'Upload failed',
          description: 'Could not process the attendance file. Please try again.',
        });
        e.target.value = '';
      } finally {
        setIsUploading(false);
      }
    }
  };

  return (
    <form action="#" method="POST">
      <div className="space-y-2">
        <Label htmlFor="file-1">
          <FileSpreadsheet className="h-4 w-4" />
          Upload Pair Programming Attendance file <span className="text-destructive">*</span>
        </Label>
        <Input id="file-1" name="file-1" type="file" accept=".xlsx" className="pt-2" onChange={handleFileChange} disabled={isUploading} />
        {isUploading && (
          <p className="text-sm text-muted-foreground flex items-center gap-2">
            <Loader2 className="h-4 w-4 animate-spin" />
            Uploading and parsing attendance...
          </p>
        )}
        {error && <p className="text-sm text-destructive">{error}</p>}
        <p className="text-sm text-muted-foreground">You are only allowed to upload XLSX files.</p>
      </div>
    </form>
  );
}

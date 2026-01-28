// import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FileSpreadsheet } from 'lucide-react';
import { useState } from 'react';

export default function FileUpload() {
  const [error, setError] = useState('');

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const fileExtension = file.name.split('.').pop()?.toLowerCase();
      if (fileExtension !== 'xlsx') {
        setError('Only XLSX files are allowed');
        e.target.value = ''; // Clear the input
      } else {
        setError('');
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
          <Input
              id="file-1"
              name="file-1"
              type="file"
              accept=".xlsx"
              className="pt-2"
              onChange={handleFileChange}
          />
          {error && (
              <p className="text-sm text-destructive">{error}</p>
          )}
          <p className="text-sm text-muted-foreground">
            You are only allowed to upload XLSX files.
          </p>
        </div>
      </form>
  );
}
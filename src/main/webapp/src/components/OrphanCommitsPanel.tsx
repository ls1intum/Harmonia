import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ChevronDown, ChevronUp, AlertTriangle, GitCommit, User, FileCode, Check, X } from 'lucide-react';
import type { OrphanCommitDTO, AnalyzedChunkDTO } from '@/app/generated';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import type { StudentAnalysisDTO } from '@/app/generated';

export interface EmailMapping {
  id: string;
  exerciseId: number;
  gitEmail: string;
  studentId: number;
  studentName: string;
}

interface OrphanCommitsPanelProps {
  commits: OrphanCommitDTO[];
  analysisHistory?: AnalyzedChunkDTO[];
  students: StudentAnalysisDTO[];
  exerciseId?: string;
  teamParticipationId?: string;
  emailMappings: EmailMapping[];
  onMappingChange: () => void;
  templateAuthorEmail?: string;
}

const OrphanCommitsPanel = ({
  commits,
  analysisHistory,
  students,
  exerciseId,
  teamParticipationId,
  emailMappings,
  onMappingChange,
  templateAuthorEmail,
}: OrphanCommitsPanelProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const [selectedStudents, setSelectedStudents] = useState<Record<string, string>>({});
  const [loadingEmails, setLoadingEmails] = useState<Set<string>>(new Set());
  const [removingIds, setRemovingIds] = useState<Set<string>>(new Set());

  const templateEmailLower = templateAuthorEmail?.toLowerCase();

  // Derive external chunks from analysisHistory for this team (excluding template author)
  const externalChunks =
    analysisHistory?.filter(c => c.isExternalContributor && c.authorEmail && c.authorEmail.toLowerCase() !== templateEmailLower) || [];

  // Get unique orphan emails from commits OR from external chunks (excluding template author)
  const orphanEmailsFromCommits = new Set(
    commits.map(c => c.authorEmail?.toLowerCase()).filter((e): e is string => !!e && e !== templateEmailLower),
  );
  const orphanEmailsFromChunks = new Set(
    externalChunks.map(c => c.authorEmail?.toLowerCase()).filter((e): e is string => !!e && e !== templateEmailLower),
  );
  const allOrphanEmails = new Set([...orphanEmailsFromCommits, ...orphanEmailsFromChunks]);

  // Mapped emails for this team (from exercise-wide mappings that match orphan emails here)
  const mappedEmails = emailMappings.filter(m => allOrphanEmails.has(m.gitEmail.toLowerCase()));
  const mappedEmailSet = new Set(mappedEmails.map(m => m.gitEmail.toLowerCase()));

  // Unmapped orphan emails
  const unmappedEmails = [...allOrphanEmails].filter(e => e && !mappedEmailSet.has(e));

  // Group commits by email
  const commitsByEmail = new Map<string, OrphanCommitDTO[]>();
  for (const commit of commits) {
    const email = commit.authorEmail?.toLowerCase() || 'unknown';
    if (!commitsByEmail.has(email)) {
      commitsByEmail.set(email, []);
    }
    commitsByEmail.get(email)!.push(commit);
  }

  const totalOrphanCount = unmappedEmails.length;
  const totalMappedCount = mappedEmails.length;
  const totalCount = totalOrphanCount + totalMappedCount;

  if (totalCount === 0 && commits.length === 0) {
    return null;
  }

  const totalLines = commits.reduce((sum, c) => sum + (c.linesAdded ?? 0) + (c.linesDeleted ?? 0), 0);

  const handleAssign = async (email: string) => {
    const studentName = selectedStudents[email];
    if (!studentName || !exerciseId || !teamParticipationId) return;

    // Find student ID - we need to look up by name since StudentAnalysisDTO doesn't have ID
    // The backend will use studentId=0 as a placeholder since we pass the name
    setLoadingEmails(prev => new Set(prev).add(email));

    try {
      const response = await fetch(`/api/exercises/${exerciseId}/email-mappings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          gitEmail: email,
          studentId: 0, // We use name-based lookup on the backend
          studentName: studentName,
          teamParticipationId: teamParticipationId,
        }),
      });

      if (response.ok) {
        onMappingChange();
        setSelectedStudents(prev => {
          const next = { ...prev };
          delete next[email];
          return next;
        });
      }
    } catch (error) {
      console.error('Failed to create email mapping:', error);
    } finally {
      setLoadingEmails(prev => {
        const next = new Set(prev);
        next.delete(email);
        return next;
      });
    }
  };

  const handleRemove = async (mappingId: string) => {
    if (!exerciseId) return;

    setRemovingIds(prev => new Set(prev).add(mappingId));

    try {
      const response = await fetch(`/api/exercises/${exerciseId}/email-mappings/${mappingId}`, {
        method: 'DELETE',
        credentials: 'include',
      });

      if (response.ok || response.status === 204) {
        onMappingChange();
      }
    } catch (error) {
      console.error('Failed to remove email mapping:', error);
    } finally {
      setRemovingIds(prev => {
        const next = new Set(prev);
        next.delete(mappingId);
        return next;
      });
    }
  };

  return (
    <Card className="border-amber-200 bg-amber-50/50 dark:bg-amber-950/10 mb-6">
      <CardHeader
        className="py-3 px-4 cursor-pointer hover:bg-amber-100/50 dark:hover:bg-amber-900/20 transition-colors"
        onClick={() => setIsOpen(!isOpen)}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-5 h-5 text-amber-700 dark:text-amber-400" />
            <CardTitle className="text-base font-medium text-foreground dark:text-amber-200">
              {totalOrphanCount > 0
                ? `${totalOrphanCount} Unmatched Email${totalOrphanCount !== 1 ? 's' : ''}`
                : `${totalMappedCount} Mapped Email${totalMappedCount !== 1 ? 's' : ''}`}
            </CardTitle>
            {totalMappedCount > 0 && totalOrphanCount > 0 && (
              <Badge className="bg-green-100 text-green-700 border-green-200">{totalMappedCount} mapped</Badge>
            )}
          </div>
          <div className="flex items-center gap-3">
            {totalLines > 0 && <span className="text-sm text-muted-foreground dark:text-amber-300">{totalLines} lines</span>}
            <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
              {isOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
            </Button>
          </div>
        </div>
      </CardHeader>

      {isOpen && (
        <CardContent className="px-4 pb-4 pt-0">
          <p className="text-sm text-muted-foreground mb-4">
            These commits were made with email addresses that don't match any registered team member. You can manually assign them to a
            student below.
          </p>

          {/* Mapped Emails Section */}
          {mappedEmails.length > 0 && (
            <div className="mb-4">
              <h4 className="text-sm font-semibold text-green-800 dark:text-green-300 mb-2">Mapped Emails</h4>
              <div className="space-y-2">
                {mappedEmails.map(mapping => (
                  <div
                    key={mapping.id}
                    className="flex items-center justify-between bg-green-50 dark:bg-green-950/20 rounded-lg border border-green-200 dark:border-green-800 p-3"
                  >
                    <div className="flex items-center gap-2">
                      <Check className="w-4 h-4 text-green-600" />
                      <code className="text-sm">{mapping.gitEmail}</code>
                      <span className="text-sm text-muted-foreground">→</span>
                      <span className="text-sm font-medium">{mapping.studentName}</span>
                      <Badge className="bg-green-100 text-green-700 border-green-200">Mapped</Badge>
                    </div>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleRemove(mapping.id)}
                      disabled={removingIds.has(mapping.id)}
                      className="text-red-600 hover:text-red-700 hover:bg-red-50"
                    >
                      <X className="h-4 w-4 mr-1" />
                      {removingIds.has(mapping.id) ? 'Removing...' : 'Remove'}
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Unmapped Orphan Emails Section */}
          {unmappedEmails.length > 0 && (
            <div>
              {mappedEmails.length > 0 && (
                <h4 className="text-sm font-semibold text-amber-800 dark:text-amber-300 mb-2">Unmapped Emails</h4>
              )}
              <div className="max-h-[300px] w-full overflow-y-auto pr-2">
                <div className="space-y-3">
                  {unmappedEmails.map(email => {
                    if (!email) return null;
                    const emailCommits = commitsByEmail.get(email) || [];

                    return (
                      <div key={email} className="bg-background rounded-lg border border-amber-100 dark:border-amber-900/50 p-3">
                        {/* Email header with assignment */}
                        <div className="flex items-center justify-between gap-2 mb-2">
                          <Badge variant="outline" className="text-xs gap-1">
                            <User className="w-3 h-3" />
                            {email}
                          </Badge>
                          {exerciseId && teamParticipationId && (
                            <div className="flex items-center gap-2">
                              <Select
                                value={selectedStudents[email] || ''}
                                onValueChange={v => setSelectedStudents(prev => ({ ...prev, [email]: v }))}
                              >
                                <SelectTrigger className="h-8 w-[180px] text-xs">
                                  <SelectValue placeholder="Assign to student..." />
                                </SelectTrigger>
                                <SelectContent>
                                  {students.map(s => (
                                    <SelectItem key={s.name} value={s.name ?? ''}>
                                      {s.name}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                              <Button
                                size="sm"
                                className="h-8 text-xs"
                                onClick={() => handleAssign(email)}
                                disabled={!selectedStudents[email] || loadingEmails.has(email)}
                              >
                                {loadingEmails.has(email) ? 'Assigning...' : 'Assign'}
                              </Button>
                            </div>
                          )}
                        </div>

                        {/* Commits for this email */}
                        {emailCommits.map(commit => (
                          <div key={commit.commitHash ?? ''} className="flex items-start gap-3 mt-2 pl-2">
                            <GitCommit className="w-4 h-4 mt-1 text-muted-foreground shrink-0" />
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2 flex-wrap mb-1">
                                <code className="text-xs bg-muted px-1.5 py-0.5 rounded font-mono">
                                  {(commit.commitHash ?? '').substring(0, 7)}
                                </code>
                                <span className="text-xs text-muted-foreground ml-auto">
                                  {new Date(commit.timestamp ?? new Date().toISOString()).toLocaleString()}
                                </span>
                              </div>
                              <p className="text-sm font-medium truncate mb-1">{commit.message ?? ''}</p>
                              <div className="flex items-center gap-3 text-xs text-muted-foreground">
                                <span className="flex items-center gap-1">
                                  <FileCode className="w-3 h-3" />
                                  <span className="text-green-600">+{commit.linesAdded ?? 0}</span>
                                  {' / '}
                                  <span className="text-red-600">-{commit.linesDeleted ?? 0}</span>
                                </span>
                                {commit.authorName && <span>by {commit.authorName}</span>}
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          )}
        </CardContent>
      )}
    </Card>
  );
};

export default OrphanCommitsPanel;

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ChevronDown, ChevronUp, AlertTriangle, GitCommit, User, FileCode, Check, X, EyeOff } from 'lucide-react';
import type { OrphanCommitDTO, AnalyzedChunkDTO, EmailMappingDTO, StudentAnalysisDTO } from '@/app/generated';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { emailMappingApi } from '@/lib/apiClient';
import { useMutation } from '@tanstack/react-query';

interface OrphanCommitsPanelProps {
  commits: OrphanCommitDTO[];
  analysisHistory?: AnalyzedChunkDTO[];
  students: StudentAnalysisDTO[];
  exerciseId?: string;
  teamParticipationId?: string;
  emailMappings: EmailMappingDTO[];
  onMappingChange: () => void;
  templateAuthorEmail?: string;
}

/**
 * Collapsible panel for orphan (unmatched-email) commits.
 *
 * Allows tutors to assign orphan emails to known students
 * and to remove existing mappings.
 */
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

  const assignMutation = useMutation({
    mutationFn: async ({ email, studentName }: { email: string; studentName: string }) => {
      if (!exerciseId || !teamParticipationId) throw new Error('Missing IDs');
      await emailMappingApi.createMapping(parseInt(exerciseId), {
        gitEmail: email,
        studentId: 0,
        studentName,
        teamParticipationId: parseInt(teamParticipationId),
      });
      return email;
    },
    onSuccess: email => {
      onMappingChange();
      setSelectedStudents(prev => {
        const next = Object.assign({}, prev);
        delete next[email];
        return next;
      });
    },
  });

  const removeMutation = useMutation({
    mutationFn: async (mappingId: string) => {
      if (!exerciseId) throw new Error('Missing exercise ID');
      await emailMappingApi.deleteMapping(parseInt(exerciseId), mappingId);
    },
    onSuccess: () => {
      onMappingChange();
    },
  });

  const dismissMutation = useMutation({
    mutationFn: async (email: string) => {
      if (!exerciseId || !teamParticipationId) throw new Error('Missing IDs');
      await emailMappingApi.dismissEmail(parseInt(exerciseId), {
        gitEmail: email,
        teamParticipationId: parseInt(teamParticipationId),
      });
      return email;
    },
    onSuccess: () => {
      onMappingChange();
    },
  });

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
  // Include mapping emails that have chunks in this team (even after assignment mutates is_external)
  const allChunkEmails = new Set(
    (analysisHistory ?? []).map(c => c.authorEmail?.toLowerCase()).filter((e): e is string => !!e && e !== templateEmailLower),
  );
  const mappingEmails = emailMappings
    .filter(m => m.gitEmail && allChunkEmails.has(m.gitEmail.toLowerCase()))
    .map(m => m.gitEmail!.toLowerCase());
  const allOrphanEmails = new Set([...orphanEmailsFromCommits, ...orphanEmailsFromChunks, ...mappingEmails]);

  // Split mappings into assigned and dismissed
  const assignedMappings = emailMappings.filter(m => !m.isDismissed && m.gitEmail && allOrphanEmails.has(m.gitEmail.toLowerCase()));
  const dismissedMappings = emailMappings.filter(m => m.isDismissed && m.gitEmail && allOrphanEmails.has(m.gitEmail.toLowerCase()));
  const mappedEmailSet = new Set(assignedMappings.concat(dismissedMappings).map(m => (m.gitEmail ?? '').toLowerCase()));

  // Unmapped orphan emails
  const unmappedEmails = Array.from(allOrphanEmails).filter(e => e && !mappedEmailSet.has(e));

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
  const totalAssignedCount = assignedMappings.length;
  const totalDismissedCount = dismissedMappings.length;
  const totalCount = totalOrphanCount + totalAssignedCount + totalDismissedCount;

  if (totalCount === 0 && commits.length === 0) {
    return null;
  }

  const totalLines = commits.reduce((sum, c) => sum + (c.linesAdded ?? 0) + (c.linesDeleted ?? 0), 0);

  const handleAssign = (email: string) => {
    const studentName = selectedStudents[email];
    if (!studentName || !exerciseId || !teamParticipationId) return;
    assignMutation.mutate({ email, studentName });
  };

  const handleDismiss = (email: string) => {
    dismissMutation.mutate(email);
  };

  const handleRemove = (mappingId: string) => {
    if (!exerciseId) return;
    removeMutation.mutate(mappingId);
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
                : totalAssignedCount > 0
                  ? `${totalAssignedCount} Mapped Email${totalAssignedCount !== 1 ? 's' : ''}`
                  : `${totalDismissedCount} Dismissed Email${totalDismissedCount !== 1 ? 's' : ''}`}
            </CardTitle>
            {totalAssignedCount > 0 && totalOrphanCount > 0 && (
              <Badge className="bg-green-100 text-green-700 border-green-200">{totalAssignedCount} mapped</Badge>
            )}
            {totalDismissedCount > 0 && totalOrphanCount > 0 && (
              <Badge className="bg-slate-100 text-slate-600 border-slate-200">{totalDismissedCount} dismissed</Badge>
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
            These commits were made with email addresses that don't match any registered team member. You can assign them to a student or
            dismiss them.
          </p>

          {/* Mapped Emails Section */}
          {assignedMappings.length > 0 && (
            <div className="mb-4">
              <h4 className="text-sm font-semibold text-green-800 dark:text-green-300 mb-2">Mapped Emails</h4>
              <div className="space-y-2">
                {assignedMappings.map(mapping => (
                  <div
                    key={mapping.id ?? ''}
                    className="flex items-center justify-between bg-green-50 dark:bg-green-950/20 rounded-lg border border-green-200 dark:border-green-800 p-3"
                  >
                    <div className="flex items-center gap-2">
                      <Check className="w-4 h-4 text-green-600" />
                      <code className="text-sm">{mapping.gitEmail ?? ''}</code>
                      <span className="text-sm text-muted-foreground">&rarr;</span>
                      <span className="text-sm font-medium">{mapping.studentName ?? ''}</span>
                      <Badge className="bg-green-100 text-green-700 border-green-200">Mapped</Badge>
                    </div>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleRemove(mapping.id ?? '')}
                      disabled={removeMutation.isPending && removeMutation.variables === (mapping.id ?? '')}
                      className="text-red-600 hover:text-red-700 hover:bg-red-50"
                    >
                      <X className="h-4 w-4 mr-1" />
                      {removeMutation.isPending && removeMutation.variables === (mapping.id ?? '') ? 'Removing...' : 'Remove'}
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Unmapped Orphan Emails Section */}
          {unmappedEmails.length > 0 && (
            <div className="mb-4">
              {(assignedMappings.length > 0 || dismissedMappings.length > 0) && (
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
                                onValueChange={v => setSelectedStudents(prev => Object.assign({}, prev, { [email]: v }))}
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
                                disabled={
                                  !selectedStudents[email] || (assignMutation.isPending && assignMutation.variables?.email === email)
                                }
                              >
                                {assignMutation.isPending && assignMutation.variables?.email === email ? 'Assigning...' : 'Assign'}
                              </Button>
                              <Button
                                variant="outline"
                                size="sm"
                                className="h-8 text-xs text-slate-600 hover:text-slate-700"
                                onClick={() => handleDismiss(email)}
                                disabled={dismissMutation.isPending && dismissMutation.variables === email}
                              >
                                <EyeOff className="h-3.5 w-3.5 mr-1" />
                                {dismissMutation.isPending && dismissMutation.variables === email ? 'Dismissing...' : 'Dismiss'}
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

          {/* Dismissed Emails Section */}
          {dismissedMappings.length > 0 && (
            <div>
              <h4 className="text-sm font-semibold text-slate-600 dark:text-slate-400 mb-2">Dismissed Emails</h4>
              <div className="space-y-2">
                {dismissedMappings.map(mapping => (
                  <div
                    key={mapping.id ?? ''}
                    className="flex items-center justify-between bg-slate-50 dark:bg-slate-950/20 rounded-lg border border-slate-200 dark:border-slate-800 p-3"
                  >
                    <div className="flex items-center gap-2">
                      <EyeOff className="w-4 h-4 text-slate-400" />
                      <code className="text-sm text-slate-500">{mapping.gitEmail ?? ''}</code>
                      <Badge className="bg-slate-100 text-slate-500 border-slate-200">Dismissed</Badge>
                    </div>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleRemove(mapping.id ?? '')}
                      disabled={removeMutation.isPending && removeMutation.variables === (mapping.id ?? '')}
                      className="text-slate-600 hover:text-slate-700 hover:bg-slate-100"
                    >
                      {removeMutation.isPending && removeMutation.variables === (mapping.id ?? '') ? 'Undoing...' : 'Undo'}
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </CardContent>
      )}
    </Card>
  );
};

export default OrphanCommitsPanel;

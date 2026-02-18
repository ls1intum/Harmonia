import type { StudentAnalysisDTO, CQIResultDTO, AnalyzedChunkDTO, OrphanCommitDTO, LlmTokenTotals } from '@/app/generated';

export interface SubMetric {
  name: string;
  value: number;
  weight: number;
  description: string;
  details: string;
  status?: 'FOUND' | 'NOT_FOUND' | 'WARNING' | null; // Pair programming status from attendance upload/analysis
}

export interface BasicMetrics {
  totalCommits: number;
  totalLines: number;
}

export interface AnalysisError {
  id: string;
  authorEmail: string;
  timestamp: string;
  errorMessage: string;
  commitShas: string[];
}

export interface Team {
  id: string;
  teamName: string;
  tutor: string;
  submissionCount?: number;
  analysisStatus?: 'PENDING' | 'DOWNLOADING' | 'GIT_ANALYZING' | 'GIT_DONE' | 'AI_ANALYZING' | 'ANALYZING' | 'DONE' | 'ERROR' | 'CANCELLED';
  students: StudentAnalysisDTO[];
  cqi?: number;
  isSuspicious?: boolean;
  cqiDetails?: CQIResultDTO;
  llmTokenTotals?: LlmTokenTotals;
  subMetrics?: SubMetric[];
  basicMetrics?: BasicMetrics;
  analysisHistory?: AnalyzedChunkDTO[];
  orphanCommits?: OrphanCommitDTO[];
}

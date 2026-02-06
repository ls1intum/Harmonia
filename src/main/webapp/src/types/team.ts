import type { StudentAnalysisDTO, CQIResultDTO, AnalyzedChunkDTO, OrphanCommitDTO } from '@/app/generated';

export interface SubMetric {
  name: string;
  value: number;
  weight: number;
  description: string;
  details: string;
}

export interface BasicMetrics {
  totalCommits: number;
  totalLines: number;
}

export interface AnalyzedChunk {
  id: string;
  authorEmail: string;
  authorName: string;
  classification: string;
  effortScore: number;
  reasoning: string;
  commitShas: string[];
  commitMessages: string[];
  timestamp: string;
  linesChanged: number;
  isBundled: boolean;
  chunkIndex: number;
  totalChunks: number;
  isError?: boolean;
  errorMessage?: string;
}

export interface AnalysisError {
  id: string; // Reuse chunk ID
  authorEmail: string;
  timestamp: string;
  errorMessage: string;
  commitShas: string[];
}

export interface OrphanCommit {
  commitHash: string;
  authorEmail: string;
  authorName: string;
  message: string;
  timestamp: string;
  linesAdded: number;
  linesDeleted: number;
}

export interface Team {
  id: string;
  teamName: string;
  tutor: string;
  submissionCount?: number;
  students: Student[];
  cqi?: number;
  isSuspicious?: boolean;
  cqiDetails?: CQIResultDTO;
  subMetrics?: SubMetric[];
  basicMetrics?: BasicMetrics;
  analysisHistory?: AnalyzedChunk[];
  orphanCommits?: OrphanCommit[];
}

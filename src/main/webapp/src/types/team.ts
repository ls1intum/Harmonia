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
  analysisStatus?: 'PENDING' | 'ANALYZING' | 'DONE' | 'ERROR' | 'CANCELLED';
  students: StudentAnalysisDTO[];
  cqi?: number;
  isSuspicious?: boolean;
  cqiDetails?: CQIResultDTO;
  subMetrics?: SubMetric[];
  basicMetrics?: BasicMetrics;
  analysisHistory?: AnalyzedChunkDTO[];
  orphanCommits?: OrphanCommitDTO[];
}

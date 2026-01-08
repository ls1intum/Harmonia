export interface Student {
  name: string;
  commits?: number;
  linesAdded?: number;
  linesDeleted?: number;
  linesChanged?: number;
}

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
}

export interface Team {
  id: string;
  teamName: string;
  tutor: string;
  students: Student[];
  cqi?: number;
  isSuspicious?: boolean;
  subMetrics?: SubMetric[];
  basicMetrics?: BasicMetrics;
  analysisHistory?: AnalyzedChunk[];
}


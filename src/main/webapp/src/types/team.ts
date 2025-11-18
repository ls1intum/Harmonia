export interface Student {
  name: string;
  commits?: number;
  linesAdded?: number;
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

export interface Team {
  id: string;
  teamName: string;
  students: Student[];
  cqi?: number;
  isSuspicious?: boolean;
  subMetrics?: SubMetric[];
  basicMetrics?: BasicMetrics;
}

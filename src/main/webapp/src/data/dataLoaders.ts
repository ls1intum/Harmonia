import type { ClientResponseDTO, CQIResultDTO } from '@/app/generated';

export interface SubMetric {
  name: string;
  value: number;
  weight: number;
  description: string;
  details: string;
  status?: 'FOUND' | 'NOT_FOUND' | 'WARNING' | null;
}

/** A ClientResponseDTO extended with client-computed sub-metrics. */
export type TeamDTO = ClientResponseDTO & { subMetrics?: SubMetric[] };

// ============================================================
// DATA TRANSFORMATION - Convert DTO to Client Types
// ============================================================

/**
 * Transform a raw ClientResponseDTO into a TeamDTO with computed CQI fields
 * and sub-metrics derived from CQI detail components.
 */
export function transformToComplexTeamData(dto: ClientResponseDTO): TeamDTO {
  // 1) Determine analysis completeness
  const isNotFullyAnalyzed = dto.analysisStatus === 'GIT_DONE' || dto.analysisStatus === 'AI_ANALYZING';

  // 2) CQI: only valid when fully analyzed and non-negative
  const rawCqi = dto.cqi;
  const cqi = isNotFullyAnalyzed ? undefined : rawCqi !== undefined && rawCqi !== null && rawCqi >= 0 ? Math.round(rawCqi) : undefined;

  // 3) isSuspicious: only valid when fully analyzed
  const isSuspicious = isNotFullyAnalyzed ? undefined : (dto.isSuspicious ?? undefined);

  // 4) Build sub-metrics from CQI detail components
  const serverCqiDetails = dto.cqiDetails as CQIResultDTO | undefined;
  const weights = serverCqiDetails?.weights;
  const pairProgrammingStatus = serverCqiDetails?.components?.pairProgrammingStatus;
  const showPairProgramming =
    pairProgrammingStatus === 'FOUND' || pairProgrammingStatus === 'NOT_FOUND' || pairProgrammingStatus === 'WARNING';

  const subMetrics: SubMetric[] | undefined = serverCqiDetails?.components
    ? [
        {
          name: 'Effort Balance',
          value: isNotFullyAnalyzed ? -1 : Math.round(serverCqiDetails.components.effortBalance ?? 0),
          weight: Math.round((weights?.effortBalance ?? 0) * 100),
          description: 'Is effort distributed fairly among team members?',
          details: isNotFullyAnalyzed
            ? 'Requires AI analysis. Will be calculated after git analysis completes for all teams.'
            : 'Based on LLM-weighted contribution analysis. Higher scores indicate balanced workload distribution.',
        },
        {
          name: 'Lines of Code Balance',
          value: Math.round(serverCqiDetails.components.locBalance ?? 0),
          weight: Math.round((weights?.locBalance ?? 0) * 100),
          description: 'Are code contributions balanced?',
          details: 'Measures the distribution of lines added/deleted across team members.',
        },
        {
          name: 'Temporal Spread',
          value: Math.round(serverCqiDetails.components.temporalSpread ?? 0),
          weight: Math.round((weights?.temporalSpread ?? 0) * 100),
          description: 'Is work spread over time or crammed at deadline?',
          details: 'Higher scores mean work was spread consistently throughout the project period.',
        },
        {
          name: 'File Ownership Spread',
          value: Math.round(serverCqiDetails.components.ownershipSpread ?? 0),
          weight: Math.round((weights?.ownershipSpread ?? 0) * 100),
          description: 'Are files owned by multiple team members?',
          details: 'Measures how well files are shared among team members (based on git blame analysis).',
        },
        ...(showPairProgramming
          ? [
              {
                name: 'Pair Programming',
                value:
                  pairProgrammingStatus === 'FOUND'
                    ? Math.round(serverCqiDetails.components.pairProgramming ?? 0)
                    : pairProgrammingStatus === 'WARNING'
                      ? -3
                      : -2,
                weight: 0,
                description: 'Did both students commit during pair programming sessions?',
                details:
                  pairProgrammingStatus === 'FOUND'
                    ? 'Verifies that both team members actually collaborated by checking if they both made commits on the dates when they attended pair programming tutorials together.'
                    : pairProgrammingStatus === 'WARNING'
                      ? 'Some pair-programming tutorials were cancelled, so mandatory attendance could not be evaluated reliably. Some sessions were attended.'
                      : 'Team not found in attendance Excel file. Please check that the team name in the Excel matches exactly.',
                status: pairProgrammingStatus as 'FOUND' | 'NOT_FOUND' | 'WARNING',
              },
            ]
          : []),
      ]
    : undefined;

  return {
    ...dto,
    cqi,
    isSuspicious,
    subMetrics,
  };
}

// ============================================================
// PUBLIC API - Data Loaders
// ============================================================

/**
 * Stream team data from server via SSE
 * The streaming flow is:
 * 1. START - total count of teams
 * 2. INIT - each team with pending status (no CQI)
 * 3. PHASE - phase change notification (GIT_ANALYSIS or AI_ANALYSIS)
 * 4. GIT_ANALYZING - a team's git analysis is starting
 * 5. GIT_UPDATE - a team's git analysis is complete (with commits/lines, no CQI)
 * 6. GIT_DONE - all git analysis complete
 * 7. AI_ANALYZING - a team's AI analysis is starting
 * 8. AI_UPDATE - a team's AI analysis is complete (with CQI)
 * 9. DONE - all analysis complete
 */
export interface TemplateAuthorInfo {
  email: string;
  autoDetected: boolean;
}

export function loadBasicTeamDataStream(
  exerciseId: string,
  onStart: (total: number) => void,
  onInit: (team: TeamDTO) => void,
  onUpdate: (team: TeamDTO | Partial<ClientResponseDTO>) => void,
  onComplete: () => void,
  onError: (error: unknown) => void,
  onPhaseChange?: (phase: 'GIT_ANALYSIS' | 'AI_ANALYSIS', total: number) => void,
  onGitDone?: (processed: number) => void,
  onTemplateAuthor?: (info: TemplateAuthorInfo) => void,
  onTemplateAuthorAmbiguous?: (candidates: string[]) => void,
): () => void {
  const eventSource = new EventSource(`/api/requestResource/stream?exerciseId=${exerciseId}`, {
    withCredentials: true,
  });

  eventSource.onmessage = event => {
    try {
      const data = JSON.parse(event.data);
      if (data.type === 'START') {
        onStart(data.total);
      } else if (data.type === 'INIT') {
        // Teams arriving with pending status (no CQI yet)
        onInit(transformToComplexTeamData(data.data));
      } else if (data.type === 'PHASE') {
        // Phase change notification
        if (onPhaseChange) {
          onPhaseChange(data.phase, data.total);
        }
      } else if (data.type === 'GIT_ANALYZING') {
        // A specific team's git analysis is starting
        onUpdate({
          teamId: data.teamId,
          teamName: data.teamName,
          analysisStatus: 'GIT_ANALYZING' as const,
        });
      } else if (data.type === 'GIT_UPDATE') {
        // Git analysis complete for this team - has commits/lines but no CQI
        const team = transformToComplexTeamData(data.data);
        onUpdate({ ...team, analysisStatus: 'GIT_DONE', cqi: undefined, isSuspicious: undefined });
      } else if (data.type === 'GIT_DONE') {
        // All git analysis complete
        if (onGitDone) {
          onGitDone(data.processed);
        }
      } else if (data.type === 'TEMPLATE_AUTHOR') {
        if (onTemplateAuthor) {
          onTemplateAuthor({ email: data.email, autoDetected: data.autoDetected });
        }
      } else if (data.type === 'TEMPLATE_AUTHOR_AMBIGUOUS') {
        if (onTemplateAuthorAmbiguous) {
          onTemplateAuthorAmbiguous(data.candidates);
        }
      } else if (data.type === 'AI_ANALYZING') {
        // A specific team's AI analysis is starting
        onUpdate({
          teamId: data.teamId,
          teamName: data.teamName,
          analysisStatus: 'AI_ANALYZING' as const,
          cqi: undefined,
          isSuspicious: undefined,
        });
      } else if (data.type === 'AI_UPDATE') {
        // AI analysis complete for this team - has CQI
        const team = transformToComplexTeamData(data.data);
        onUpdate({ ...team, analysisStatus: 'DONE' });
      } else if (data.type === 'AI_ERROR') {
        // AI analysis failed for this team - keep git data
        onUpdate({
          teamId: data.teamId,
          teamName: data.teamName,
          analysisStatus: 'GIT_DONE' as const,
          cqi: undefined,
        });
      } else if (data.type === 'ANALYZING') {
        // Legacy: A specific team is now being analyzed - update its status
        onUpdate({
          teamId: data.teamId,
          teamName: data.teamName,
          analysisStatus: 'ANALYZING' as const,
        });
      } else if (data.type === 'UPDATE') {
        // Legacy: Server sends ClientResponseDTO with CQI and isSuspicious
        onUpdate(transformToComplexTeamData(data.data));
      } else if (data.type === 'DONE') {
        eventSource.close();
        onComplete();
      } else if (data.type === 'CANCELLED') {
        // Analysis was cancelled - close stream and notify completion
        eventSource.close();
        onComplete();
      } else if (data.type === 'ALREADY_RUNNING') {
        // Analysis is already running (started by another user or before refresh)
        console.log('Analysis already running, switching to polling mode');
        eventSource.close();
        onError(new Error('ALREADY_RUNNING'));
      }
    } catch (e) {
      console.error('Error parsing SSE event:', e);
      onError(e);
    }
  };

  eventSource.onerror = error => {
    console.error('SSE Error:', error);
    eventSource.close();
    onError(error);
  };

  return () => eventSource.close();
}

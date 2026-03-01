import { normalizeTeamName } from '@/lib/utils';

export type PairProgrammingAttendanceStatus = 'pass' | 'fail' | 'warning';

export type PairProgrammingAttendanceMap = Record<string, PairProgrammingAttendanceStatus>;

export type PairProgrammingBadgeStatus = 'not_found' | 'warning' | 'pass' | 'fail';

/** All badge statuses in display order for filter dropdowns and similar UI. */
export const PAIR_PROGRAMMING_BADGE_STATUSES: readonly PairProgrammingBadgeStatus[] = ['pass', 'fail', 'warning', 'not_found'] as const;

/** Display labels for each badge status. */
export const PAIR_PROGRAMMING_BADGE_STATUS_LABELS: Record<PairProgrammingBadgeStatus, string> = {
  pass: 'Pass',
  fail: 'Fail',
  warning: 'Warning',
  not_found: 'Not Found',
};

const persistedStatusToBadgeStatus: Record<string, PairProgrammingBadgeStatus> = {
  PASS: 'pass',
  FAIL: 'fail',
  NOT_FOUND: 'not_found',
  WARNING: 'warning',
};

export const getPairProgrammingAttendanceFileStorageKey = (exerciseId: string): string => {
  return `pair-programming-attendance-file:${exerciseId}`;
};

export const getPairProgrammingAttendanceMapStorageKey = (exerciseId: string): string => {
  return `pair-programming-attendance-status:${exerciseId}`;
};

export const readStoredPairProgrammingAttendanceMap = (storageKey: string): PairProgrammingAttendanceMap => {
  const rawValue = window.sessionStorage.getItem(storageKey);
  if (!rawValue) {
    return {};
  }

  try {
    const parsed: unknown = JSON.parse(rawValue);
    if (typeof parsed !== 'object' || parsed === null) {
      return {};
    }

    return Object.entries(parsed).reduce<PairProgrammingAttendanceMap>((acc, [teamName, storedStatus]) => {
      if (storedStatus === 'pass' || storedStatus === 'fail' || storedStatus === 'warning') {
        acc[normalizeTeamName(teamName)] = storedStatus;
      } else if (typeof storedStatus === 'boolean') {
        // Backward compatibility for previously stored values.
        acc[normalizeTeamName(teamName)] = storedStatus ? 'pass' : 'fail';
      }
      return acc;
    }, {});
  } catch {
    return {};
  }
};

export const hasValidPairProgrammingAttendanceData = (
  pairProgrammingEnabled: boolean,
  uploadedAttendanceFileName: string | null,
  pairProgrammingAttendanceByTeamName: PairProgrammingAttendanceMap,
): boolean => {
  return pairProgrammingEnabled && !!uploadedAttendanceFileName && Object.keys(pairProgrammingAttendanceByTeamName).length > 0;
};

export const getPairProgrammingBadgeStatus = (
  teamName: string,
  hasValidPairProgrammingData: boolean,
  pairProgrammingAttendanceByTeamName: PairProgrammingAttendanceMap,
): PairProgrammingBadgeStatus | null => {
  if (!hasValidPairProgrammingData) {
    return null;
  }

  const normalizedTeamName = normalizeTeamName(teamName);
  const hasAttendanceEntry = Object.prototype.hasOwnProperty.call(pairProgrammingAttendanceByTeamName, normalizedTeamName);

  if (!hasAttendanceEntry) {
    return 'not_found';
  }

  return pairProgrammingAttendanceByTeamName[normalizedTeamName];
};

export const getPairProgrammingBadgeStatusFromPersistedStatus = (status?: string | null): PairProgrammingBadgeStatus | null => {
  if (!status) {
    return null;
  }
  return persistedStatusToBadgeStatus[status] ?? null;
};

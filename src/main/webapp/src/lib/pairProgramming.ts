import { normalizeTeamName } from '@/lib/utils';

export type PairProgrammingAttendanceStatus = 'pass' | 'fail' | 'warning';

export type PairProgrammingAttendanceMap = Record<string, PairProgrammingAttendanceStatus>;

export type PairProgrammingBadgeStatus = 'not_found' | 'warning' | 'pass' | 'fail';

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

/**
 * Resolves badge status from the attendance map, trying team name first then short name.
 * So when the Excel is keyed by short name and the UI shows the full name, the badge still shows PASS/FAIL.
 */
export const getPairProgrammingBadgeStatus = (
  teamName: string,
  hasValidPairProgrammingData: boolean,
  pairProgrammingAttendanceByTeamName: PairProgrammingAttendanceMap,
  shortName?: string | null,
): PairProgrammingBadgeStatus | null => {
  if (!hasValidPairProgrammingData) {
    return null;
  }

  const normalizedTeamName = normalizeTeamName(teamName);
  let status: PairProgrammingAttendanceStatus | undefined =
    pairProgrammingAttendanceByTeamName[normalizedTeamName];

  if (status === undefined && shortName != null) {
    const normalizedShortName = normalizeTeamName(shortName);
    status = pairProgrammingAttendanceByTeamName[normalizedShortName];
  }

  if (status === undefined) {
    return 'not_found';
  }

  return status;
};

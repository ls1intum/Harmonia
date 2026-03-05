export type PairProgrammingBadgeStatus = 'not_found' | 'warning' | 'pass' | 'fail';

export const getPairProgrammingAttendanceFileStorageKey = (exerciseId: string): string => {
  return `pair-programming-attendance-file:${exerciseId}`;
};

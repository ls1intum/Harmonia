package de.tum.cit.aet.core.enums;

public enum PairProgrammingStatus {
    PASS,
    FAIL,
    NOT_FOUND,
    WARNING;

    /**
     * Derives the persisted pair-programming status from uploaded attendance state.
     *
     * @param hasAttendanceData whether any attendance file has been uploaded
     * @param hasCancelledSessionWarning whether cancelled sessions make evaluation unreliable
     * @param pairedMandatorySessions whether the team attended the mandatory number of paired sessions
     * @return PASS, FAIL, NOT_FOUND, WARNING
     */
    public static PairProgrammingStatus fromAttendanceState(
            boolean hasAttendanceData,
            boolean hasCancelledSessionWarning,
            boolean pairedMandatorySessions) {

        if (!hasAttendanceData) {
            return NOT_FOUND;
        }
        if (hasCancelledSessionWarning) {
            return WARNING;
        }
        return pairedMandatorySessions ? PASS : FAIL;
    }
}

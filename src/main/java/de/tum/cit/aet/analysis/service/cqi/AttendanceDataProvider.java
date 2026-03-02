package de.tum.cit.aet.analysis.service.cqi;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Read-only view of attendance data needed by the CQI calculator.
 * Implemented by {@link de.tum.cit.aet.pairProgramming.service.PairProgrammingService}.
 */
public interface AttendanceDataProvider {

    boolean hasAttendanceData();

    boolean hasTeamAttendance(String teamName);

    boolean hasCancelledSessionWarning(String teamName);

    boolean isPairedMandatorySessions(String teamName);

    Set<OffsetDateTime> getPairedSessions(String teamName);

    Set<OffsetDateTime> getClassDates(String teamName);
}

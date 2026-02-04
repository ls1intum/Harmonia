package de.tum.cit.aet.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import java.time.OffsetDateTime;

@Component
@ConfigurationProperties(prefix = "harmonia.attendance")
@Validated
@Data
public class AttendanceConfiguration {
    @Min(0)
    private int startRowIndex = 4;
    @Min(0)
    private int rowStep = 3;
    private int teamNameColumn = 0;
    private int[] student1Columns = new int[]{4, 8, 12};
    private int[] student2Columns = new int[]{5, 9, 13};
    private OffsetDateTime submissionDeadline = OffsetDateTime.parse("2026-01-28T13:00:00+01:00");;
    @Min(1)
    private int numberProgrammingSessions = 3;
}

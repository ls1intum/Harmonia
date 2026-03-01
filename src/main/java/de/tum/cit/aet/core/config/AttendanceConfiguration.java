package de.tum.cit.aet.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;

/**
 * Configuration properties for parsing attendance records ({@code harmonia.attendance.*}).
 * Defines the spreadsheet layout (row indices, column mappings) and session requirements.
 */
@Data
@Component
@ConfigurationProperties(prefix = "harmonia.attendance")
@Validated
public class AttendanceConfiguration {

    @Min(0)
    private int startRowIndex = 4;

    @Min(0)
    private int rowStep = 3;

    private int teamNameColumn = 0;
    private int[] student1Columns = new int[]{4, 8, 12};
    private int[] student2Columns = new int[]{5, 9, 13};

    @Min(1)
    private int numberProgrammingSessions = 3;

    @Min(1)
    private int mandatoryProgrammingSessions = 2;
}

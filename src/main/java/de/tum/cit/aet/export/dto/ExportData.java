package de.tum.cit.aet.export.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ExportData {

    private List<TeamExportRow> teams;
    private List<StudentExportRow> students;
    private List<ChunkExportRow> chunks;
    private List<CommitExportRow> commits;

    public ExportData(List<TeamExportRow> teams, List<StudentExportRow> students,
            List<ChunkExportRow> chunks, List<CommitExportRow> commits) {
        this.teams = teams;
        this.students = students;
        this.chunks = chunks;
        this.commits = commits;
    }
}

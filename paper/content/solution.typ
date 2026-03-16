= Our Solution: Harmonia

As described in the previous sections, the current process for evaluating student team projects in ITP suffers from a lack of scalability, consistency, and transparency. Harmonia addresses these challenges by providing instructors with an automated, evidence-based platform that aggregates collaboration metrics from Git repositories and presents them in a centralized web application. In this section, we first outline the requirements that guided the design of Harmonia, then describe the overall system architecture and its key components, and finally explain the core concepts and mechanisms that enable fair and data-driven project assessment.

== Requirements Analysis

Based on the challenges identified in the grading process, we derived the following functional and quality requirements for Harmonia. Given the scope of a seminar project, these requirements focus on the most essential capabilities needed to support instructors in evaluating student collaboration.

=== Functional Requirements

#figure(
  table(
    columns: (auto, auto, auto),
    align: (center, left, left),
    table.header(
      [*ID*], [*Requirement*], [*Description*],
    ),
    [FR-01], [Course Dashboard], [The system must provide a course-wide dashboard showing collaboration, contribution metrics, and pair programming compliance.],
    [FR-02], [CQI Configuration], [The system must allow instructors to configure, add, remove, and weight CQI indicators per course.],
    [FR-03], [Repository Integration], [The system must support analysis of Artemis repositories without requiring manual downloads.],
    [FR-04], [Manual Analysis Triggering], [The system must allow instructors to manually trigger analysis runs course-wide or per team, with real-time progress feedback.],
    [FR-05], [Team List Overview], [The system must provide a sortable and filterable list of all teams with key metrics such as total commits, lines of code, and the Collaboration Quality Index.],
    [FR-06], [Individual Team Breakdown], [The system must provide a detailed team view including commits, pair programming flags, contribution balance, and sessions.],
    [FR-07], [Pair-Programming Session Logging], [The system must allow tutors to create time-bound pair programming session logs that automatically associate commits within the logged time window.],
    [FR-08], [Commit-Level Inspection], [The system must provide commit views including diffs, CQI sub-scores, scoring explanations, and pair programming flag status.],
    [FR-09], [Normalizer and Pre-Scoring], [The system must normalize commits before scoring by detecting formatting-only changes, renames, and large mechanical changes, and exclude them from the analysis.],
  ),
  caption: [Functional requirements of Harmonia.],
) <tab:functional-requirements>

=== Quality Requirements

In addition to the functional capabilities, the following quality requirements ensure that Harmonia is practical and reliable in a real course setting.

#figure(
  table(
    columns: (auto, auto, auto),
    align: (center, left, left),
    table.header(
      [*ID*], [*Category*], [*Description*],
    ),
    [QR-01], [Analysis Completion Time], [A course-wide analysis must complete within a reasonable time, targeting less than 15 minutes for typical ITP-scale courses of up to 200 teams.],
    [QR-02], [UI Responsiveness], [All dashboard and team list interactions such as filtering, sorting, and searching must respond within two seconds.],
    [QR-03], [Large Repository Support], [The system must handle large Git repositories and teams of varying sizes without UI or analysis failure.],
    [QR-04], [Fault-Tolerant Analysis Jobs], [Analysis must gracefully handle partial failures, such as an unavailable repository, without halting the entire course run.],
    [QR-05], [Clear Visual Feedback], [The system must provide clear status feedback for analysis progress, errors, and waiting states, including job statuses such as queued, running, completed, and failed.],
    [QR-06], [Role-Based Access], [Only authorized roles such as instructors and tutors may view or trigger analyses or access team data.],
  ),
  caption: [Quality requirements of Harmonia.],
) <tab:quality-requirements>

== System Design

Harmonia is designed as a monolithic web application consisting of three main components: a server that performs the analysis and exposes a REST API, a client that provides the instructor-facing user interface, and a database that persists all analysis results. @fig:subsystem-decomposition illustrates the high-level subsystem decomposition of the system.

#figure(
  image("/figures/Subsystem_Decomposition.png", width: 100%),
  caption: [Subsystem decomposition of Harmonia.],
) <fig:subsystem-decomposition>

=== Server

The server is the core of Harmonia and is responsible for all data retrieval, analysis logic, and result computation. As shown in @fig:subsystem-decomposition, it consists of several services organized into two main subsystems.

The *Data Processing System* handles all external communication and data ingestion. The _Repository Processing Service_ clones and fetches Git repositories from Artemis, extracts commit metadata such as author, timestamp, changed files, insertions, and deletions, and computes file ownership through blame analysis. The _Authentication Service_ manages the connection to the Artemis server, handling credential resolution and session management. The _Request Processing Service_ exposes the Harmonia API, providing RESTful endpoints for the client to trigger analyses, retrieve team data, and configure scoring parameters. It also provides a Server-Sent Events (SSE) endpoint that streams real-time progress updates to the client during an analysis run.

The *Analysis System* contains the core scoring and evaluation logic. The _CQI Calculation Service_ computes the Collaboration Quality Index and all of its sub-components based on the extracted Git data. The _Pair Programming Service_ cross-references attendance records with commit activity to verify compliance with mandatory pair programming sessions. The _LLM Service_ communicates with an external AI model to support commit classification and narrative generation. Within the Analysis System, the *AI Evaluation System* houses the _Commit Evaluation Service_, which classifies individual commits by type and estimates the coding effort involved, feeding these results back into the CQI computation.

=== External Systems

As depicted in @fig:subsystem-decomposition, Harmonia communicates with two external systems. The *Artemis Server* provides access to course data, team compositions, Git repositories, and version control access logs through its API. The *LLM API* provides AI capabilities for commit classification and effort estimation, enabling the Effort Balance component of the CQI.

=== Client

The client is a single-page web application that serves as the primary interface for instructors and tutors. It communicates with the server exclusively through the REST API and the SSE stream. The interface is organized around three main views:

- *Teams Overview:* A table listing all teams in the course with their key metrics, including the CQI score, total commits, lines of code, and analysis status. The table supports sorting by any column and filtering by criteria such as pair programming compliance or flagged teams. Course-wide averages are displayed prominently to provide context for individual team scores.
// TODO: uncomment reference once figure is added: @fig:teams-overview shows this view.

// TODO: Add screenshot of teams overview page
// #figure(
//   image("/figures/teams_overview.png", width: 95%),
//   caption: [Teams overview page showing all teams with their key metrics and CQI scores.],
// ) <fig:teams-overview>

- *Team Detail View:* A dedicated page for each team that presents a comprehensive breakdown of collaboration evidence. This includes the final CQI score and all contributing sub-scores, a daily activity chart showing the distribution of commits over time, detailed contribution data per student, and the AI-generated narrative summary.

- *Analysis Control:* Instructors can trigger a new analysis directly from the interface. During an analysis run, the client displays real-time progress for each team as updates are streamed from the server, showing which teams are being downloaded, analyzed, or have completed.

=== Database

Harmonia uses a relational database to persist all analysis results. The schema stores information about teams, students, commits, file-level changes, computed scores, and analysis job metadata. Database migrations are managed through version-controlled migration scripts, ensuring that the schema evolves consistently across deployments.

== Collaboration Quality Index

The central metric produced by Harmonia is the *Collaboration Quality Index (CQI)*, a composite score between 0 and 100 that quantifies how well a team collaborated during the project. The CQI is designed to capture multiple dimensions of collaboration rather than relying on a single indicator.

=== Composition

The CQI is computed as a weighted sum of four sub-scores, each measuring a different aspect of collaboration:

#figure(
  table(
    columns: (auto, auto, auto, auto),
    align: (center, left, center, left),
    table.header(
      [*Component*], [*Description*], [*Weight*], [*Method*],
    ),
    [Effort Balance], [How evenly the weighted coding effort is distributed among team members.], [55%], [Gini coefficient over LLM-rated effort per author],
    [LoC Balance], [How evenly the total lines of code changed are distributed.], [25%], [Gini coefficient over lines changed per author],
    [Temporal Spread], [How evenly the work is distributed across the project timeline.], [5%], [Coefficient of variation over weekly effort],
    [Ownership Spread], [How broadly file ownership is shared among team members.], [15%], [Ratio of distinct authors per file to team size],
  ),
  caption: [Components of the Collaboration Quality Index.],
) <tab:cqi-components>

// TODO: Add screenshot of CQI metrics in team detail view
// #figure(
//   image("/figures/cqi_metrics.png", width: 95%),
//   caption: [CQI breakdown in the team detail view, showing the overall score and individual sub-components.],
// ) <fig:cqi-metrics>

The Effort Balance component carries the highest weight because it accounts for the qualitative significance of contributions. Rather than treating all commits equally, Harmonia uses an AI model to classify each commit and estimate the actual coding effort involved. This allows the system to distinguish between a commit that introduces a new feature and one that merely reformats existing code.

The remaining three components are computed purely from Git data. LoC Balance provides a straightforward measure of quantitative contribution, Temporal Spread penalizes teams that concentrate all their work into a short period near the deadline, and Ownership Spread rewards teams where multiple members contribute to shared files rather than working in complete isolation.

=== Calculation

Each balance-based component (Effort Balance and LoC Balance) uses the *Gini coefficient* to measure inequality in the distribution of work. A Gini coefficient of zero indicates perfect equality, while a value of one indicates that a single person did all the work. The component score is computed as $100 times (1 - G)$, where $G$ is the Gini coefficient.

Temporal Spread uses the *coefficient of variation* (the ratio of standard deviation to mean) computed over weekly effort totals. A low coefficient of variation indicates consistent work throughout the project, while a high value suggests irregular bursts of activity. The score is computed as $100 times (1 - min("CV" slash 2, 1))$.

Ownership Spread examines how many distinct authors have contributed to each significant file (those with at least three commits). The score reflects the average ratio of contributing authors to team size across all such files.

=== Edge Cases and Filtering

To ensure that the CQI reflects genuine collaboration, Harmonia applies several safeguards. Teams with only a single contributor automatically receive a CQI of zero. Similarly, if no productive commits remain after pre-filtering, the score is set to zero.

The pre-filtering step is essential for producing meaningful scores. Before any analysis, commits are automatically classified and excluded if they fall into categories that do not represent substantive work. These include empty commits, merge commits, reverts, rename-only changes, formatting-only edits, mass reformats, changes to generated files (such as lock files or build outputs), and commits with trivial messages. This filtering ensures that the CQI is based only on commits that genuinely reflect a student's coding effort.

== Artemis Integration

A key design goal of Harmonia is seamless integration with the existing course infrastructure. Since ITP uses Artemis as its learning management platform, Harmonia connects directly to the Artemis API to retrieve all necessary data.

=== Data Retrieval

When an instructor triggers an analysis, Harmonia authenticates with the Artemis server and retrieves the list of all team participations for the specified exercise. This includes the team composition (which students belong to which team), the repository URLs for each team, and metadata such as submission counts. Harmonia then clones or updates each team's Git repository directly from Artemis, eliminating the need for instructors to manually download or provide repository data.

In addition to repository access, Harmonia retrieves version control access logs from Artemis. These logs record which students pushed commits and when, providing an additional layer of evidence for verifying authorship.

=== Analysis Pipeline

The analysis pipeline follows a three-phase architecture designed to maximize throughput while providing continuous feedback to the instructor:

+ *Download Phase:* All team repositories are cloned in parallel. For each repository, the system also fetches the corresponding access logs from Artemis. Teams that fail to download are marked as failed without blocking the rest of the analysis.

+ *Git Analysis Phase:* Each successfully downloaded repository is analyzed in parallel. The system extracts commit metadata, computes per-author contribution statistics, and determines file ownership. Partial results, including the Git-based sub-scores, are persisted and made available to the instructor immediately.

+ *AI Analysis Phase:* Each team's commits are processed sequentially through the AI model to classify commit types and estimate effort. The final CQI is computed by combining the AI-based scores with the previously calculated Git-based components.

Throughout all three phases, the server streams progress updates to the client in real time using Server-Sent Events. This allows instructors to monitor the analysis as it progresses and to begin reviewing teams that have already completed analysis while others are still being processed. The pipeline is designed to be fault-tolerant: if the analysis of an individual team fails at any phase, the error is logged and the remaining teams continue to be processed.

== Pair Programming Verification

In addition to the CQI, Harmonia supports the verification of pair programming compliance. Tutors can upload attendance records from the mandatory pair programming sessions. The system then cross-references these records with the commit history to verify that both team members were actively contributing during the sessions. For each session, the system checks whether both students made commits on the corresponding day. This provides instructors with an objective basis for assessing whether teams fulfilled the pair programming requirement, complementing the qualitative assessment by tutors.

// TODO: Add pair programming screenshots
// #figure(
//   image("/figures/pair_programming_upload.png", width: 95%),
//   caption: [Attendance upload and pair programming metrics on the teams overview page.],
// ) <fig:pair-programming-upload>
//
// #figure(
//   image("/figures/pair_programming_detail.png", width: 95%),
//   caption: [Pair programming score displayed in the team detail view.],
// ) <fig:pair-programming-detail>

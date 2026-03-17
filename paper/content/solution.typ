= Our Solution: Harmonia

The grading challenges motivate an automated, evidence-based approach to evaluating student collaboration. Harmonia addresses these challenges by aggregating collaboration metrics from Git repositories and presenting them in a centralized web application. This section first outlines the requirements that guided the design of Harmonia, then describes the system architecture and its components, and finally explains the core concepts that enable fair and data-driven project assessment.

== Requirements Analysis

The grading challenges translate into concrete functional and quality requirements. @tab:functional-requirements lists the functional requirements, while @tab:quality-requirements lists the quality requirements. The scope of a seminar project necessitated a focus on the capabilities needed for supporting instructors in evaluating student collaboration. The following two subsections present these requirements in detail.

=== Functional Requirements

Nine functional requirements capture the core capabilities Harmonia must provide. These range from a course-wide dashboard (FR-01) and configurable scoring indicators (FR-02) to commit-level inspection (FR-08) and automated pre-filtering of non-substantive commits (FR-09). @tab:functional-requirements summarizes each requirement together with a short description.

#figure(
  table(
    columns: (auto, auto, auto),
    stroke: 0.6pt + black,
    align: (center, left, left),
    table.header(
      [*ID*], [*Requirement*], [*Description*],
    ),
    [FR-01], [Course Dashboard], [The system must provide a course-wide dashboard showing collaboration, contribution metrics, and pair programming compliance.],
    [FR-02], [CQI Configuration], [The system must allow instructors to configure, add, remove, and weight CQI indicators per course.],
    [FR-03], [Repository Integration], [The system must support analysis of Artemis, GitLab, and GitHub repositories without requiring manual downloads.],
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

Six quality requirements complement the functional capabilities and ensure that Harmonia remains practical and reliable in a real course setting. These requirements address performance targets, fault tolerance, usability, and access control. @tab:quality-requirements provides a summary.

#figure(
  table(
    columns: (auto, auto, auto),
    stroke: 0.6pt + black,
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

Harmonia follows a monolithic architecture with three main components: a server that performs the analysis and exposes a REST API, a client that provides the instructor-facing user interface, and a database that persists all analysis results. @fig:subsystem-decomposition illustrates the subsystem decomposition of the system. The server communicates with two external systems, namely the Artemis learning management platform and a large language model (LLM) API, while the client interacts exclusively with the server through the Harmonia API.

The following subsections describe each component and external dependency in detail. The server subsection explains the internal service architecture, the external systems subsection covers the two third-party integrations, and the client and database subsections outline the user interface layer and the persistence layer, respectively.

#figure(
  image("/figures/Subsystem_Decomposition.pdf", width: 100%),
  caption: [Subsystem decomposition of Harmonia.],
) <fig:subsystem-decomposition>

=== Server

The server forms the core of Harmonia and handles all data retrieval, analysis logic, and result computation. @fig:subsystem-decomposition shows that it consists of several services organized into two main subsystems: the Data Processing System and the Analysis System.

The *Data Processing System* manages all external communication and data ingestion. The _Repository Processing Service_ clones and fetches Git repositories from Artemis, extracts commit metadata such as author, timestamp, changed files, insertions, and deletions, and computes file ownership through blame analysis. The _Authentication Service_ manages the connection to the Artemis server and handles credential resolution and session management. The _Request Processing Service_ exposes the Harmonia API through RESTful endpoints that allow the client to trigger analyses, retrieve team data, and configure scoring parameters. This service also provides a Server-Sent Events (SSE) endpoint that streams real-time progress updates to the client during an analysis run.

The *Analysis System* contains the scoring and evaluation logic. The _CQI Calculation Service_ computes the Collaboration Quality Index and all of its sub-components from the extracted Git data. The _Pair Programming Service_ cross-references attendance records with commit activity to verify compliance with mandatory pair programming sessions. The _LLM Service_ communicates with an external AI model to support commit classification and narrative generation. Within the Analysis System, the *AI Evaluation System* houses the _Commit Evaluation Service_, which classifies individual commits by type and estimates the coding effort involved. These effort estimates feed back into the CQI computation.

=== External Systems

Harmonia communicates with two external systems, both depicted in @fig:subsystem-decomposition. The *Artemis Server* provides access to course data, team compositions, Git repositories, and version control access logs through its API. Harmonia authenticates with Artemis and retrieves all necessary data programmatically, eliminating the need for manual data exports.

The *LLM API* provides the AI capabilities required for commit classification and effort estimation. The server sends commit metadata, including the commit message, changed file paths, and diff statistics, to the LLM, which returns a classification label, an effort score, and a confidence value. These results enable the Effort Balance component of the CQI, which carries the largest weight in the overall score.

=== Client

The client is a single-page web application that serves as the primary interface for instructors and tutors. It communicates with the server exclusively through the REST API and the SSE stream. The interface organizes its functionality around three main views: a teams overview that provides a course-wide dashboard with relevant metrics, a team detail view that presents a breakdown of collaboration evidence for an individual team, and analysis controls that allow instructors to trigger and monitor analysis runs in real time. @user-interface describes the concrete realization of these views.

The client updates its state progressively during an active analysis. The SSE stream delivers status changes for each team as the server processes it, allowing instructors to review completed teams while the server continues analyzing others. Instructors do not need to wait for the full analysis to finish before beginning their review.

=== Database

Harmonia uses a relational database to persist all analysis results. The schema stores information about teams, students, commits, file-level changes, computed scores, and analysis job metadata. This separation allows the server to resume or extend analyses without reprocessing already-completed teams.

Version-controlled migration scripts manage all database schema changes, ensuring that the schema evolves consistently across deployments. The database stores all intermediate and final results, which enables the client to display partial results during an ongoing analysis and to retrieve historical results for past exercises.

== Collaboration Quality Index

The central metric Harmonia produces is the *Collaboration Quality Index (CQI)*, a composite score between 0 and 100 that quantifies how well a team collaborated during the project. The CQI captures multiple dimensions of collaboration rather than relying on a single indicator. This section describes the composition, calculation, and filtering logic behind the CQI.

=== Composition

Harmonia computes the CQI as a weighted sum of four sub-scores, each measuring a different aspect of collaboration:

#align(center)[#text(size: 0.85em)[
$ "CQI" = w_1 dot "Effort" + w_2 dot "LoC" + w_3 dot "Temporal" + w_4 dot "Ownership" $
]]

@tab:cqi-components summarizes the weights $w_1$ through $w_4$ and describes each component.

#figure(
  table(
    columns: (auto, auto, auto, auto),
    stroke: 0.6pt + black,
    align: (center, left, center, left),
    table.header(
      [*Component*], [*Description*], [*Weight*], [*Method*],
    ),
    [Effort Balance], [Measures how evenly team members share the weighted coding effort.], [55%], [Gini coefficient over LLM-rated effort per author],
    [LoC Balance], [Measures how evenly team members share the total lines of code changed.], [25%], [Gini coefficient over lines changed per author],
    [Temporal Spread], [Measures how evenly the team distributes work across the project timeline.], [5%], [Coefficient of variation over weekly effort],
    [Ownership Spread], [Measures how broadly team members share file ownership.], [15%], [Ratio of distinct authors per file to team size],
  ),
  caption: [Components of the Collaboration Quality Index.],
) <tab:cqi-components>

The Effort Balance component carries the largest weight because it accounts for the qualitative importance of contributions. Harmonia uses an AI model to classify each commit and estimate the actual coding effort involved. This mechanism allows the system to distinguish between a commit that introduces a new feature and one that merely reformats existing code. The three remaining components rely purely on Git data. LoC Balance measures quantitative contribution, Temporal Spread penalizes teams that concentrate all work into a short period near the deadline, and Ownership Spread rewards teams where multiple members contribute to shared files.

=== Calculation

The two balance-based components, Effort Balance and LoC Balance, use the *Gini coefficient* to measure inequality in the distribution of work. A Gini coefficient of zero indicates perfect equality, while a value of one indicates that a single person produced all the work. Harmonia computes the component score with the formula $100 times (1 - G)$, where $G$ denotes the Gini coefficient. A perfectly balanced team therefore receives a score of 100, while a team with a single contributor receives a score of 0.

Temporal Spread uses the *coefficient of variation* (CV), defined as the ratio of standard deviation to mean, computed over weekly effort totals. A low CV indicates consistent work throughout the project, while a high value signals irregular bursts of activity. Harmonia computes this score with the formula $100 times (1 - min("CV" slash 2, 1))$. Ownership Spread examines how many distinct authors contributed to each relevant file, where a relevant file has at least three commits. The resulting score reflects the average ratio of contributing authors to team size across all such files.

=== Edge Cases and Filtering

Harmonia applies several safeguards to ensure that the CQI reflects genuine collaboration. Teams with only a single contributor automatically receive a CQI of zero. The same applies when no productive commits remain after pre-filtering.

The pre-filtering step is necessary for producing meaningful scores. Harmonia automatically classifies and excludes commits that do not represent substantive work before any scoring takes place. The excluded categories include empty commits, merge commits, reverts, rename-only changes, formatting-only edits, mass reformats, changes to generated files such as lock files or build outputs, and commits with trivial messages. This filtering ensures that only commits reflecting genuine coding effort enter the CQI computation.

== Artemis Integration

ITP uses Artemis as its learning management platform, and Harmonia connects directly to the Artemis API to retrieve all necessary data. This integration eliminates the need for instructors to manually export or provide repository data, which is a central usability goal for the tool.

=== Data Retrieval

The instructor triggers an analysis, and Harmonia authenticates with the Artemis server to retrieve the list of all team participations for the specified exercise. The response contains the team composition, that is, which students belong to which team, along with the repository URLs and metadata such as submission counts. Harmonia then clones or updates each team's Git repository directly from Artemis.

Harmonia retrieves version control access logs from Artemis alongside the repository data. These logs record which students pushed commits and when, providing an additional layer of evidence for verifying authorship. The combination of repository content and access logs gives the analysis pipeline a complete picture of each team's development activity.

=== Analysis Pipeline

The analysis pipeline follows a three-phase architecture designed to maximize throughput while providing continuous feedback to the instructor. The first phase, the *Download Phase*, clones all team repositories in parallel and fetches the corresponding access logs from Artemis. Teams that fail to download receive a failure status without blocking the remaining teams. The second phase, the *Git Analysis Phase*, processes each downloaded repository in parallel by extracting commit metadata, computing per-author contribution statistics, and determining file ownership. The server persists partial results, including the Git-based sub-scores, and makes them available to the instructor immediately.

The third phase, the *AI Analysis Phase*, processes each team's commits sequentially through the AI model to classify commit types and estimate effort. The server then computes the final CQI by combining the AI-based scores with the Git-based components from the previous phase. Throughout all three phases, the server streams progress updates to the client in real time using Server-Sent Events. Instructors can monitor the analysis as it progresses and begin reviewing completed teams while others are still being processed. The pipeline handles failures gracefully: if the analysis of an individual team fails at any phase, the server logs the error and continues processing the remaining teams.

== Pair Programming Verification

Harmonia supports the verification of pair programming compliance alongside the CQI. The ITP course requires students to attend mandatory pair programming sessions, and verifying this attendance manually is time-consuming for tutors and instructors.

Tutors upload attendance records from the mandatory pair programming sessions into Harmonia. The system then cross-references these records with the commit history to verify that both team members actively contributed during the sessions. For each session, Harmonia checks whether both students made commits on the corresponding day. This check provides instructors with an objective basis for assessing whether teams fulfilled the pair programming requirement, complementing the qualitative observations that tutors provide during the sessions.

== User Interface <user-interface>

The Harmonia web application translates the metrics and concepts from the preceding sections into an interactive interface for instructors. The interface guides instructors from a high-level course overview down to individual commit-level evidence. The following subsections walk through each view.

=== Teams Overview

@fig:teams-overview shows the main page of Harmonia. The top bar provides controls for starting a new analysis run and for uploading pair programming attendance documents. Uploading an attendance file triggers an automatic compliance check across all teams. The table below lists every team in the course along with relevant metrics: the CQI score, total commits, lines of code, and the current analysis status. Instructors can sort the table by any column and apply filters for criteria such as pair programming compliance or flagged teams.

Course-wide averages appear prominently above the table to provide context for individual team scores. During an active analysis, the table updates in real time as teams progress through the download, Git analysis, and AI analysis phases. This real-time feedback allows instructors to begin reviewing completed teams without waiting for the full course analysis to finish.

#figure(
  image("/figures/Teams_Overview.png", width: 95%),
  caption: [Teams overview page. The top bar provides controls for starting an analysis and uploading pair programming attendance. The table below lists all teams with their CQI scores, commit counts, and analysis status.],
) <fig:teams-overview>

=== Team Detail View

Instructors navigate from the overview to the detail page for any individual team. @fig:team-detail shows this page, which presents a breakdown of the team's collaboration metrics. Five score cards appear at the top: Effort Balance, Lines of Code Balance, Temporal Spread, File Ownership Spread, and Pair Programming. Each card displays the computed score alongside its weight in the overall CQI, enabling instructors to identify which aspects of collaboration are strong and which may require attention.

The score cards translate the abstract CQI sub-components into a visual format that supports quick assessment. Instructors can compare the scores across components to determine, for example, whether a low CQI stems from uneven effort distribution or from a lack of shared file ownership. This breakdown makes the CQI transparent and actionable rather than presenting a single opaque number.

#figure(
  image("/figures/TeamDetail_Page.png", width: 95%),
  caption: [Team detail page showing the five CQI sub-component scores: Effort Balance, LoC Balance, Temporal Spread, Ownership Spread, and Pair Programming.],
) <fig:team-detail>

=== AI Analysis Feed

The team detail page includes the AI Analysis Feed below the score cards, shown in @fig:ai-feed. This feed lists every commit that each team member contributed, after AI analysis. For each commit, the feed displays the AI-assigned effort rating, the number of lines changed, the commit date, and the category type. Instructors can scan the full contribution history and identify patterns such as disproportionate effort or commits classified as low-effort.

#figure(
  image("/figures/Ai_AnalysisFeed.png", width: 95%),
  caption: [AI Analysis Feed showing each commit with its AI-assigned effort rating, lines changed, date, and category.],
) <fig:ai-feed>

Instructors can expand any entry in the feed to view the AI Analysis Detail, shown in @fig:ai-detail. This view presents the full AI reasoning for that commit chunk, including the effort score, complexity rating, novelty assessment, and the model's confidence level. Exposing the AI reasoning ensures that the scoring process remains transparent. Instructors can verify the automated assessment and identify cases where manual review may be warranted.

#figure(
  image("/figures/Ai_AnalysisFeed_Detail.png", width: 95%),
  caption: [Expanded AI analysis detail for a single commit chunk, showing effort score, complexity, novelty, confidence, and the AI reasoning.],
) <fig:ai-detail>

== Implementation Status

@tab:implementation-status summarizes the fulfillment of each requirement defined in @tab:functional-requirements and @tab:quality-requirements. The scope of a seminar project necessitated descoping certain requirements. The table and the discussion below explain the current status and the rationale behind each gap.

#figure(
  table(
    columns: (auto, auto, auto, auto),
    stroke: 0.6pt + black,
    align: (center, left, center, left),
    table.header(
      [*ID*], [*Requirement*], [*Status*], [*Remarks*],
    ),
    [FR-01], [Course Dashboard], [Fulfilled], [],
    [FR-02], [CQI Configuration], [Fulfilled], [],
    [FR-03], [Repository Integration], [Partial], [Only Artemis is supported; the team did not implement GitLab and GitHub integration.],
    [FR-04], [Manual Analysis Triggering], [Fulfilled], [],
    [FR-05], [Team List Overview], [Fulfilled], [],
    [FR-06], [Individual Team Breakdown], [Fulfilled], [],
    [FR-07], [Pair-Programming Session Logging], [Partial], [Harmonia analyzes pair programming based on an uploaded attendance file, but tutors must create this file manually and cannot log sessions directly in the tool.],
    [FR-08], [Commit-Level Inspection], [Fulfilled], [],
    [FR-09], [Normalizer and Pre-Scoring], [Fulfilled], [],
    table.hline(),
    [QR-01], [Analysis Completion Time], [Not fulfilled], [A full course analysis takes up to 24 hours due to sequential AI processing, far exceeding the 15-minute target.],
    [QR-02], [UI Responsiveness], [Fulfilled], [],
    [QR-03], [Large Repository Support], [Partial], [The system handles typical repositories but lacks validation against repositories at the upper end of the size spectrum.],
    [QR-04], [Fault-Tolerant Analysis Jobs], [Fulfilled], [],
    [QR-05], [Clear Visual Feedback], [Fulfilled], [],
    [QR-06], [Role-Based Access], [Not fulfilled], [The tool restricts access to instructors only; the team descoped role differentiation between instructors and tutors.],
  ),
  caption: [Implementation status of functional and quality requirements.],
) <tab:implementation-status>

The partially fulfilled and unfulfilled requirements reflect deliberate scope decisions. Harmonia limits Repository Integration (FR-03) to Artemis because ITP exclusively uses Artemis, making support for other platforms unnecessary for the initial deployment. Pair-Programming Session Logging (FR-07) relies on an externally prepared attendance file, which is functional but requires manual effort from tutors outside the system.

The Analysis Completion Time (QR-01) represents the largest open gap. The AI model processes commits sequentially, causing the end-to-end runtime to scale linearly with the number of teams and commits. Parallelizing the AI processing or introducing batched requests would reduce this runtime and constitutes a key area for future optimization. The team descoped Role-Based Access (QR-06) to focus development effort on the core analysis capabilities, given that instructors currently operate the tool exclusively.

= Student View <student_view>

Harmonia addresses the instructor's assessment and transparency challenge by aggregating collaboration metrics and providing a data-driven overview of team performance. Equally important, however, is supporting students themselves during development. Discrete, summative feedback delivered only at project submission offers limited opportunity for learning and improvement. This chapter presents a complementary system that delivers formative feedback to students in real time through large language models, enabling continuous code quality assessment and iteration.

The student feedback system provides low-friction access to code quality critique throughout the project lifecycle. By integrating with the course's online development environment, it removes barriers to feedback requests and presents guidance at the moment of need. The system's non-graded, constructive nature encourages students to seek critical feedback without apprehension, supporting a growth mindset and deeper engagement with code quality principles.

== Requirements Analysis

The student feedback system operates under fundamentally different design principles than Harmonia. Whereas Harmonia produces grading-oriented metrics and instructor insights, the feedback system prioritizes learning support through actionable, balanced critique delivered in real time. This distinction shapes both functional and quality requirements. @tab:student-functional-requirements enumerates the functional capabilities, @tab:student-quality-requirements specifies the quality attributes that ensure usability and learning effectiveness.

=== Functional Requirements

#figure(
  table(
    columns: (auto, auto, auto),
    stroke: 0.6pt + black,
    align: (center, left, left),
    table.header(
      [*ID*], [*Requirement*], [*Description*],
    ),
    [FR-01], [Request Feedback Button], [The system must provide a simple, accessible mechanism (such as a button in the IDE) by which students can initiate code quality assessment on demand.],
    [FR-02], [Repository Retrieval], [The system must automatically fetch the current repository state; students must not be required to manually export or upload code.],
    [FR-03], [Analysis Execution], [The system must process submitted code through an LLM that evaluates code quality, design patterns, and adherence to learning objectives.],
    [FR-04], [Feedback Generation], [The system must categorize all feedback into four priority tiers---critical, major, minor, and nice to have---to facilitate student triage and effort allocation.],
    [FR-05], [Feedback Display], [The system must present feedback both as an aggregated list and as line-level inline annotations within the IDE.],
    [FR-06], [Unscored Assessment], [Feedback must be explicitly designated as educational guidance, not graded assessment, to encourage students to seek feedback without fear of evaluation penalties.],
  ),
  caption: [Functional requirements for the student feedback system.],
) <tab:student-functional-requirements>

=== Quality Requirements

#figure(
  table(
    columns: (auto, auto, auto),
    stroke: 0.6pt + black,
    align: (center, left, left),
    table.header(
      [*ID*], [*Category*], [*Description*],
    ),
    [QR-01], [Performance], [Feedback generation and delivery must complete within a reasonable timeframe (target: under five minutes) to provide timely guidance during development.],
    [QR-02], [Scalability], [The system must process concurrent requests from all course teams without significant performance degradation.],
    [QR-03], [Reliability], [System failures must not result in loss of previously generated feedback; historical results must remain accessible to students.],
    [QR-04], [Usability], [Feedback language and priority categories must be comprehensible to students at the skill level of introductory programming courses.],
    [QR-05], [Maintainability], [The LLM prompt, priority definitions, and assessment logic must be version-controlled and modifiable to adapt to different courses or assessment goals.],
    [QR-06], [Understandability], [Each feedback item must reference specific code locations and provide sufficient context for improvement action, yet must not disclose solution implementations.],
  ),
  caption: [Quality requirements for the student feedback system.],
) <tab:student-quality-requirements>

== System Design

The student feedback system enhances the existing course infrastructure by introducing a specialized assessment module within Athena, the AI-based assessment platform already integrated with the learning management system. This approach reuses proven deployment and management patterns while adding code quality evaluation capabilities.

=== Architecture Overview

The system comprises three core functional units: a user-facing request mechanism, an LLM-driven analysis engine, and a feedback delivery subsystem.

The *Request Interface* is implemented as a button within the online code editor, allowing students to submit their current code for analysis. Upon activation, the system captures the repository state (specifically the diff between the student's implementation and the problem template) and routes it to the analysis pipeline. This integration minimizes friction; students need not navigate away from their IDE or perform manual code preparation.

The *Analysis Engine* is realized as a containerized Athena module (written in Python and executed in Docker) that orchestrates the feedback generation process. The module first constructs a unified diff of the student's current code against the reference template. It then assembles a prompt containing the problem statement, a reference to coding standards and style expectations, and the diff. This prompt is submitted to a large language model with demonstrated proficiency in code analysis. The model returns feedback in structured format, which the module parses and organizes into the four priority categories (Critical, Major, Minor, Nice to Have).

The *Feedback Delivery Layer* transmits the categorized feedback back to the learning platform, making it available to students through two complementary visualizations: an aggregated list view for overall assessment, and inline IDE annotations linked to specific lines of code.

#figure(
  image("/figures/student_view_architecture.png", width: 100%),
  caption: [Athena Assessment Service architecture showing the Code Quality LLM module alongside other assessment modules, managed by the Assessment Module Manager and accessed via the Athena API.],
) <fig:student-view-architecture>

=== Design Rationale

Several design choices reflect the learning-centric purpose of this system:

1. *Non-Evaluative Framing*: Feedback is explicitly presented as pedagogical guidance, not assessment or grades. This removes evaluation anxiety and encourages students to request feedback frequently without fear of grading consequences.

2. *Balanced Coverage*: The LLM prompt includes guidance to balance feedback, ensuring neither excessive nor minimal criticism. Suggestions target principles, code organization, and design patterns rather than prescribing specific "correct" implementations.

3. *Granular Context*: By grounding feedback in specific code locations and diffs, the system ties suggestions directly to the student's work, enhancing clarity and relevance.

4. *Pedagogical Alignment*: The prompt instructs the LLM to adopt the perspective of a human tutor unfamiliar with official solutions, reinforcing the learning goal that students develop independent, problem-solving skills rather than reproducing reference implementations.

== Feedback Taxonomy

To enable effective student triage, all feedback is classified into one of four priority categories, each with explicit criteria and implications:

- *Critical Issue*: Defects that compromise correctness or safety—security vulnerabilities, runtime crashes, data loss, or incorrect output. These must be resolved before submission.

- *Major Issue*: Substantial code quality degradations, performance bottlenecks, or deviations from essential coding standards. Recommended for remediation before submission.

- *Minor Issue*: Code smells, non-critical style violations, or readability enhancements. Worth considering as time allows during development.

- *Nice to Have*: Stylistic refinements, polish, or optional improvements that do not affect functionality or maintainability. Can be deferred or omitted if time is constrained.

Each feedback item is prefixed with its priority, enabling students to allocate development effort efficiently and focus first on the most impactful improvements.

== User Interface and Interaction

The feedback interface employs a two-level presentation strategy that balances overview and detail:

=== Feedback Request Initiation

Within the IDE, a distinctly labeled button ("Request AI Feedback") provides the entry point for analysis. Upon activation, the system displays a progress indicator and confirmation that processing has begun. This provides clear feedback to students about system state and manages expectations regarding response latency.

=== Summary View

Upon completion, the system presents an aggregated list of identified issues. Each item displays the priority category (distinguished by color for rapid visual parsing), a concise explanation, the affected file name, and the corresponding line number(s). This list view enables students to quickly assess the scope of feedback and prioritize remediation efforts.

=== Inline Annotations

When students open or edit a source file, feedback relevant to that file appears as interactive inline annotations at the exact lines of code in question. Each annotation displays the priority category, a brief explanation, and (where applicable) the specific code construct or pattern that triggered the finding. This integrated presentation supports in-situ code improvement, reducing context switching and cognitive load.

== Implementation Status

@tab:student-implementation-status summarizes the realization of each functional and quality requirement. The student feedback system has been integrated into the course infrastructure and deployed for use during the winter semester 2025/26.

#figure(
  table(
    columns: (auto, auto, auto, auto),
    stroke: 0.6pt + black,
    align: (center, left, center, left),
    table.header(
      [*ID*], [*Requirement*], [*Status*], [*Remarks*],
    ),
    [FR-01], [Request Feedback Button], [Fulfilled], [],
    [FR-02], [Repository Retrieval], [Fulfilled], [],
    [FR-03], [Analysis Execution], [Fulfilled], [],
    [FR-04], [Feedback Generation], [Fulfilled], [],
    [FR-05], [Feedback Display], [Fulfilled], [],
    [FR-06], [Unscored Assessment], [Fulfilled], [],
    table.hline(),
    [QR-01], [Performance], [Fulfilled], [],
    [QR-02], [Scalability], [Fulfilled], [],
    [QR-03], [Reliability], [Fulfilled], [],
    [QR-04], [Usability], [Fulfilled], [],
    [QR-05], [Maintainability], [Fulfilled], [],
    [QR-06], [Understandability], [Not Fulfilled], [Feedback items reference specific locations and provide context, but some descriptions require additional explanation for full comprehension by beginners.],
  ),
  caption: [Implementation status of student feedback system requirements.],
) <tab:student-implementation-status>

The system has proven effective in supporting student learning during development. The non-graded framing encourages early and frequent feedback requests, and the priority taxonomy helps students focus improvement efforts efficiently. Minor enhancements to terminology clarity and scalability under peak load remain opportunities for refinement.

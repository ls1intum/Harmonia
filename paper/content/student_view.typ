= Student View <student_view>

Harmonia addresses the instructor's assessment and transparency challenge by aggregating collaboration metrics and providing a data-driven overview of team performance. Students themselves also benefit from support during development. Discrete, summative feedback delivered only at project submission offers limited opportunity for learning and improvement. This chapter presents a complementary system that delivers formative feedback to students in real time through large language models, enabling continuous code quality assessment and iteration.

The student feedback system provides low-friction access to code quality critique throughout the project lifecycle. By integrating with the course's online development environment, it removes barriers to feedback requests and presents guidance at the moment of need. The system frames all feedback as non-graded, constructive guidance, which encourages students to seek critique without apprehension and supports deeper engagement with code quality principles.

== Requirements Analysis

The student feedback system operates under different design principles than Harmonia. Harmonia produces grading-oriented metrics and instructor insights, while the feedback system prioritizes learning support through actionable, balanced critique delivered in real time. This distinction shapes both functional and quality requirements. @tab:student-functional-requirements enumerates the functional capabilities, and @tab:student-quality-requirements specifies the quality attributes that ensure usability and learning effectiveness.

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

#figure(
  table(
    columns: (auto, auto, auto),
    stroke: 0.6pt + black,
    align: (center, left, left),
    table.header(
      [*ID*], [*Category*], [*Description*],
    ),
    [QR-01], [Performance], [Feedback generation and delivery must complete within a reasonable timeframe (target: under five minutes) to provide timely guidance during development.],
    [QR-02], [Scalability], [The system must process concurrent requests from all course teams without performance degradation.],
    [QR-03], [Reliability], [System failures must not result in loss of previously generated feedback; historical results must remain accessible to students.],
    [QR-04], [Usability], [Feedback language and priority categories must be comprehensible to students at the skill level of introductory programming courses.],
    [QR-05], [Maintainability], [The LLM prompt, priority definitions, and assessment logic must be version-controlled and modifiable to adapt to different courses or assessment goals.],
    [QR-06], [Understandability], [Each feedback item must reference specific code locations and provide sufficient context for improvement action, yet must not disclose solution implementations.],
  ),
  caption: [Quality requirements for the student feedback system.],
) <tab:student-quality-requirements>

== System Design

The student feedback system enhances the existing course infrastructure by introducing a specialized assessment module within Athena, the AI-based assessment platform already integrated with the learning management system @soelch2026scaling. This approach reuses established deployment and management patterns while adding code quality evaluation capabilities. The system comprises three core functional units: a user-facing request mechanism, an LLM-driven analysis engine, and a feedback delivery subsystem. @fig:student-view-architecture illustrates the architecture.

The *Request Interface* takes the form of a button within the online code editor, allowing students to submit their current code for analysis. The system captures the repository state, specifically the diff between the student's implementation and the problem template, and routes it to the analysis pipeline. This integration minimizes friction, as students need not navigate away from their IDE or perform manual code preparation. The *Feedback Delivery Layer* transmits the categorized feedback back to the learning platform, making it available through two complementary visualizations: an aggregated list view for overall assessment, and inline IDE annotations linked to specific lines of code.

The *Analysis Engine* runs as a containerized Athena module written in Python and executed in Docker that orchestrates the feedback generation process. The module first constructs a unified diff of the student's current code against the reference template. It then assembles a prompt containing the problem statement, a reference to coding standards and style expectations, and the diff. The prompt instructs the LLM to adopt the perspective of a human tutor unfamiliar with official solutions and to balance feedback, ensuring neither excessive nor minimal criticism. The module submits this prompt to a large language model with proficiency in code analysis, which returns feedback in structured format that the module parses and organizes into the priority categories described in the following section.

The system grounds all feedback in specific code locations and diffs, tying suggestions directly to the student's work to enhance clarity and relevance. Suggestions target principles, code organization, and design patterns rather than prescribing specific "correct" implementations. This reinforces the learning goal that students develop independent problem-solving skills rather than reproducing reference implementations.

#figure(
  image("/figures/student_view_architecture.png", width: 100%),
  caption: [Athena Assessment Service architecture showing the Code Quality LLM module alongside other assessment modules, managed by the Assessment Module Manager and accessed via the Athena API.],
) <fig:student-view-architecture>

== Feedback Taxonomy

The system classifies all feedback into one of four priority categories, each with explicit criteria. A *Critical Issue* denotes defects that compromise correctness or safety, such as security vulnerabilities, runtime crashes, data loss, or incorrect output, and students must resolve these before submission. A *Major Issue* covers substantial code quality degradations, performance bottlenecks, or deviations from coding standards, and the system recommends remediation before submission. A *Minor Issue* refers to code smells, non-critical style violations, or readability enhancements worth considering as time allows during development. A *Nice to Have* marks stylistic refinements or optional improvements that do not affect functionality and that students can defer if time runs short. Each feedback item carries its priority as a prefix, enabling students to allocate development effort and focus first on the most impactful improvements.

== User Interface and Interaction

Within the IDE, a distinctly labeled button ("Request AI Feedback") provides the entry point for analysis. The system displays a progress indicator and confirmation that processing has begun, giving students clear feedback about the system state and managing expectations regarding response latency. After processing completes, the system presents an aggregated list of identified issues, where each item displays the priority category distinguished by color for rapid visual parsing, a concise explanation, the affected file name, and the corresponding line numbers. This list view enables students to quickly assess the scope of feedback and prioritize remediation efforts.

When students open or edit a source file, feedback relevant to that file appears as interactive inline annotations at the exact lines of code in question. Each annotation displays the priority category, a brief explanation, and where applicable the specific code construct or pattern that triggered the finding. This integrated presentation supports in-situ code improvement and reduces context switching during development.

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

= Future Work
This section outlines directions for extending Harmonia and the student feedback system beyond their current capabilities.

== Tutor View

Harmonia currently requires instructors to upload pair programming attendance records as external files, which tutors must prepare manually outside the system. A dedicated tutor view would allow tutors to log pair programming sessions directly within Harmonia, selecting the date, duration, and attendees for each session as shown in the mockup in @fig:tutor-view-log. The system would then associate commits within the logged time window automatically, replacing the manual file-based workflow and reducing effort for both tutors and instructors.

The tutor view would also provide each tutor with a dashboard displaying only their assigned teams, as shown in @fig:tutor-view-dashboard. This dashboard would surface metrics that Harmonia already computes, such as CQI scores, commit counts, and collaboration status, giving tutors direct visibility into their teams' progress without requiring access to the full instructor view. A role-based login would restrict each tutor's view to their own teams, addressing the currently unfulfilled Role-Based Access requirement (QR-06).

#figure(
  image("/figures/Tutor_View_1.png", width: 80%),
  caption: [Mockup of the tutor dashboard showing assigned teams with CQI scores, commit counts, and collaboration status.],
) <fig:tutor-view-dashboard>

#figure(
  image("/figures/Tutor_View_2.png", width: 80%),
  caption: [Mockup of the tutor session logging interface for recording pair programming attendance directly within Harmonia.],
) <fig:tutor-view-log>

== Code Quality Score

The student feedback system currently presents all feedback as ungraded guidance. A future extension could introduce a Code Quality Score that aggregates the priority-categorized feedback items into a single numeric value, as shown in the mockup in @fig:student-view-future. This score would give students a quantitative indication of their code quality alongside the detailed feedback. Unlike the CQI, which measures collaboration and targets instructors, the Code Quality Score would target students and reflect code-level concerns such as correctness, style, and design. Offering a score creates a middle ground between completely ungraded feedback and formal assessment, motivating students to iterate on their code while preserving the non-punitive character of the feedback system.

#figure(
  image("/figures/Student_View_FutureWork.png", width: 95%),
  caption: [Mockup of the student feedback view with a Code Quality Score displayed alongside the categorized feedback items.],
) <fig:student-view-future>


== Performance and Analysis Time

The sequential AI analysis phase forms the primary bottleneck, with full course analysis requiring up to 24 hours for 200 teams. Batching commit requests to the LLM API in parallel rather than processing them sequentially could reduce this overhead. A hybrid approach that applies fast heuristics for obvious cases such as formatting-only changes and merge commits, and routes only ambiguous commits to the LLM, could further accelerate processing while maintaining assessment quality.

== Platform Integration and Portability

Harmonia currently integrates exclusively with Artemis due to ITP's platform choice. Implementing GitLab and GitHub support would enable adoption in institutions using those platforms. Defining a standardized export format for CQI scores and collaboration evidence would also facilitate integration with other grading systems and research tools, reducing dependence on platform-specific implementations.

== Adversarial Robustness

An open question is whether students can deliberately game the CQI through strategic commit practices, such as splitting a single feature into many small commits or padding commit messages with inflated effort claims. Investigating these edge cases matters for assessment validity. Detection mechanisms based on code diff size, semantic consistency of commit messages, or statistical anomaly detection could harden the metric against manipulation.

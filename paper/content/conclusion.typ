= Conclusion

This paper presented Harmonia, an instructor-facing tool for automated, data-driven assessment of student collaboration in team-based software engineering projects, alongside a complementary student-facing feedback system that provides formative code quality guidance. Together, these systems address critical gaps in the current evaluation process for team projects in introductory programming courses.

== Key Contributions

Harmonia introduces several significant innovations to the field of software engineering education. First, it provides a *comprehensive, multi-dimensional assessment* of collaboration through the Collaboration Quality Index (CQI), which combines effort-based metrics (weighted at 55%) with lines of code balance, temporal spread, and file ownership spread. This approach moves beyond simple commit counting to capture the semantic quality of contributions, distinguishing between a high-effort feature implementation and a trivial formatting change through LLM-driven analysis.

Second, Harmonia achieves *transparency and scalability* by automating the assessment of up to 200 teams in a single course run. The real-time analysis pipeline with Server-Sent Events feedback allows instructors to monitor progress and begin reviewing teams before the full analysis completes. The separation of Git analysis from AI analysis enables partial results to be available immediately, reducing instructor wait time.

Third, the system demonstrates *practical integration* with existing course infrastructure. By connecting directly to the Artemis learning management platform and automating data retrieval, Harmonia eliminates the manual export burden that has historically hindered instructor adoption of assessment tools. Tutors can upload pair programming attendance records and automatically verify compliance across all teams.

Finally, the student-facing feedback system provides a *learning-centric complement* to instructor assessment. By delivering non-graded, priority-categorized feedback in real time, the system encourages early and frequent feedback requests without evaluation anxiety, supporting a growth mindset and deeper engagement with code quality principles throughout the project lifecycle.

== Evaluation Results

Our manual evaluation of five representative teams provided validation of Harmonia's approach. The inter-rater reliability study showed strong agreement between independent reviewers across most teams and dimensions, with an average Pearson correlation above 70% for effort, complexity, and novelty assessments. Team A1 demonstrated exceptional reviewer agreement (up to 99% on some dimensions), suggesting that well-structured commits are consistently recognizable, while Team H showed the greatest disagreement (70% average), highlighting the inherent subjectivity in assessing complex work. These results suggest that Harmonia's LLM-based assessments operate within a reasonable range of human judgment variability.

== Impact and Adoption

Harmonia has been deployed and used in the ITP course for the winter semester 2025/26. The tool has provided instructors with unprecedented visibility into team collaboration patterns across the course, enabling more transparent and consistent grading decisions. By surfacing collaboration imbalances and commit quality issues through the CQI and visual breakdowns, Harmonia has helped instructors identify teams requiring intervention and justify grading decisions with evidence.

The student feedback system has similarly shown positive adoption patterns, with students utilizing the "Request AI Feedback" button frequently throughout the project lifecycle. The priority taxonomy has proven effective in helping students allocate effort, and the integration with the IDE has minimized friction in seeking guidance.

== Limitations and Considerations

Despite its contributions, Harmonia has inherent limitations that should be acknowledged. The reliance on LLM-based effort estimation introduces a dependency on model quality and training data. While the paper evaluated Harmonia using GPT-5-Mini, the approach's effectiveness with other models remains unexplored. Additionally, the sequential AI analysis phase causes the overall analysis time to scale linearly with team and commit counts, exceeding the initial 15-minute target (currently taking up to 24 hours for a full course).

The CQI, while comprehensive, remains a composite metric that can mask important qualitative differences in collaboration. A team with high CQI might nevertheless have divided responsibilities without knowledge sharing, or conversely, might share code ownership through effective pair programming despite imbalanced commit counts. Instructors must therefore treat the CQI as an *indicator* warranting investigation rather than a definitive judgment.

Furthermore, Git history reflects only authorship and timestamps, not the actual effort or time invested by each student. Commits pushed by one student might represent collaborative work, and vice versa. While pair programming verification and the AI effort estimation mitigate this limitation, no purely automated system can fully capture collaboration quality.

== Broader Implications

Harmonia contributes to a growing body of work on using large language models to support software engineering education. The success of LLM-driven effort estimation in this context suggests broader applicability to other educational domains requiring nuanced assessment of student work. The open question of how to calibrate LLM-based scoring against human judgment—and how much variation is acceptable—remains important for future tool development.

The integration of both instructor-facing and student-facing tools demonstrates the complementary roles of summative and formative feedback. While Harmonia provides instructors with evidence for grading, the student feedback system supports learning throughout the process. This two-sided approach aligns with pedagogical best practices and could serve as a model for other courses.

## Conclusion

Harmonia represents a significant step forward in automating and democratizing the assessment of student collaboration in team-based software projects. By combining automated Git analysis with LLM-driven effort estimation, and by providing both instructor and student perspectives, the tool addresses longstanding challenges in software engineering education. The deployment in ITP demonstrates practical feasibility, while the manual evaluation provides evidence of reasonable alignment with human judgment.

As software engineering education continues to scale and emphasizes collaboration more prominently, tools like Harmonia will become increasingly important for maintaining assessment quality and fairness at scale. The approach pioneered in this work provides a foundation for future research and practice in this area.

= Future Work

While Harmonia provides a comprehensive solution to collaborative assessment in team-based software engineering projects, several avenues remain for further research and development. This section outlines key directions for enhancement of both the system itself and the underlying methodologies.

== Optimization and Performance

The most pressing technical limitation is the analysis completion time. The current sequential AI analysis phase causes end-to-end runtime to scale linearly with team and commit counts, with full course analysis taking up to 24 hours. Three approaches could address this bottleneck:

1. *Parallel AI Processing*: Rather than processing commits sequentially, the system could batch multiple commits and submit them to the LLM API in parallel. This would require careful orchestration to stay within rate limits and to manage costs, but could reduce runtime by an order of magnitude.

2. *Distributed Analysis*: Deploying multiple analysis workers on separate machines could process different teams in true parallel, further reducing total runtime. This would require a message queue (e.g., RabbitMQ, Apache Kafka) and careful state management.

3. *Model Caching and Heuristics*: Pre-computing effort estimates for common commit patterns (e.g., boilerplate changes, common library updates) could avoid redundant LLM calls. A hybrid approach using fast heuristics for obvious cases and LLM analysis only for ambiguous commits could dramatically improve performance.

== Model Calibration and Validation

This work used GPT-5-Mini for effort estimation, but the generalizability to other models remains unknown. Future work should:

1. *Comparative Model Study*: Evaluate Harmonia with multiple LLM backends (Claude, Gemini, open-source models like Llama) to understand how model choice affects CQI reliability and whether conclusions generalize.

2. *Extended Ground Truth Evaluation*: Expand the manual evaluation beyond the current five teams to a larger sample, ideally across multiple course offerings and programming languages. This would provide more robust calibration data.

3. *Effort Estimation Validation*: Conduct controlled studies where actual student effort (via time tracking or surveys) is compared against LLM effort estimates, providing direct validation of a key CQI component.

4. *Temporal Analysis*: Investigate whether AI effort ratings remain consistent across semesters and course contexts, or whether periodic recalibration is necessary.

== Enhanced Collaboration Metrics

The current CQI captures four aspects of collaboration, but additional dimensions could enrich the assessment:

1. *Code Review Quality*: Integrating pull request comments and code review patterns (if teams use these) could surface collaborative practices beyond commit history.

2. *Communication Analysis*: Analyzing team chat logs or commit messages for evidence of coordination and knowledge sharing could capture soft collaboration aspects missed by current metrics.

3. *Shared Ownership Dynamics*: Beyond file-level ownership, analyzing whether one student consistently refactors or improves the work of the other could reveal peer learning and mentorship.

4. *Knowledge Breadth*: Tracking whether both team members have contributed to all functional areas of the codebase could identify specialization imbalances.

== Expanded Integration and Interoperability

Currently, Harmonia integrates only with Artemis due to ITP's exclusive use of the platform. However, broader applicability requires:

1. *Multi-Platform Support*: Implement GitLab and GitHub integrations to serve courses using those platforms, as mentioned in the descoped FR-03.

2. *LMS Integration*: Extend integration to other learning management systems (Canvas, Blackboard, Moodle) to reach a broader population of courses.

3. *Data Export and Standards*: Define standardized formats for exporting CQI scores and collaboration evidence, enabling integration with other grading systems and research tools.

== Tutor and Student-Facing Enhancements

Harmonia currently focuses on instructor assessment, but expanding its scope to support other stakeholders could amplify impact:

1. *Tutor Dashboard*: Create a tutor-specific view that highlights problematic teams, shows pair programming compliance status, and surfaces commits requiring investigation. This would reduce the tutor's workload in monitoring team progress.

2. *Student Aggregate Feedback*: Provide teams with a privacy-respecting view of their own CQI over time, showing how their collaboration metrics evolve throughout the project. This could support reflection and improvement.

3. *Peer Feedback Integration*: Incorporate peer evaluation tools (e.g., CATME) and correlate peer feedback with CQI scores, validating whether collaboration metrics align with teammate perceptions.

4. *Visualization Improvements*: Expand the AI Analysis Feed with more detailed visualizations of effort distribution, temporal patterns, and ownership spread, making collaboration patterns more intuitive to grasp.

== Methodological Directions

From a research perspective, several questions remain open:

1. *Fairness and Bias in AI Assessment*: Investigate whether LLM effort ratings reflect inherent biases based on code style, programming language constructs, or commit message length. Conduct fairness audits to ensure ratings are equitable across different student populations and coding styles.

2. *Learning Outcomes Correlation*: Conduct longitudinal studies to correlate Harmonia's collaboration metrics with long-term learning outcomes, code quality in subsequent courses, and career success in team settings.

3. *Manipulation Resistance*: Explore edge cases where students might game the CQI (e.g., by making many small commits with inflated effort claims). Develop detection methods for manipulation attempts.

4. *Cross-Disciplinary Application*: Adapt Harmonia's approach to assess collaboration in non-programming domains such as hardware design, game development, or data science projects where version control usage varies.

== Expansion to Other Courses and Contexts

While validated in ITP, Harmonia's design principles could extend to other educational contexts:

1. *Advanced Software Engineering Courses*: Apply Harmonia to upper-level capstone projects where team sizes are larger and project complexity is higher.

2. *Industry and Research Settings*: Adapt Harmonia for use in research labs or software companies to support code review processes and collaboration assessment in professional contexts.

3. *Remote and Asynchronous Teams*: Investigate how Harmonia's metrics perform for fully remote teams with asynchronous collaboration patterns, where pair programming takes different forms.

== Long-Term Vision

The ultimate goal of tools like Harmonia is to support more equitable, transparent, and learning-focused assessment in team-based education. Future work should strive toward:

1. *Fully Automated Grading Pipelines*: Seamlessly integrate Harmonia with grading systems to automatically propose grades with evidence, reducing instructor burden while maintaining human oversight and review.

2. *Adaptive Feedback Systems*: Use CQI and collaboration patterns to dynamically adjust student feedback recommendations in real time (e.g., suggesting students request more feedback if collaboration imbalance is detected).

3. *Multi-Modal Assessment*: Combine Harmonia with other assessment tools (code quality checkers, test coverage analysis, design review systems) into a unified assessment platform that provides a holistic view of student projects.

4. *Ethical and Transparent AI in Education*: Develop best practices for integrating LLMs into educational assessment in ways that are transparent to students, educationally beneficial, and aligned with institutional values around fairness and integrity.

== Conclusion on Future Work

The challenges and opportunities outlined above demonstrate that while Harmonia represents a significant advance, the field of automated collaboration assessment in software engineering education is still in its early stages. Addressing these directions will make Harmonia and similar tools more practical, generalizable, and impactful. As large language models continue to evolve and as educational practice increasingly embraces data-driven assessment, the methods pioneered here provide a foundation for future innovation.

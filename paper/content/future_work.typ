= Future Work

== Performance and Analysis Time

The sequential AI analysis phase is the primary bottleneck, with full course analysis requiring up to 24 hours for 200 teams. Batching commit requests to the LLM API in parallel, rather than processing them sequentially, could substantially reduce this overhead. A hybrid approach that applies fast heuristics for obvious cases (formatting-only changes, merge commits) and routes only ambiguous commits to the LLM could further accelerate processing while maintaining assessment quality.

== Model Generalization

The current implementation relies exclusively on GPT-5-Mini for effort estimation. It remains unknown whether other LLM backends—such as Claude, Gemini, or open-source models—produce equivalent or divergent effort ratings. A comparative study across different model families would clarify whether the CQI's validity depends on a specific model or whether the approach generalizes across the LLM landscape.

== Expanded Validation

The inter-rater evaluation in this work assessed only five teams using two independent human reviewers. Expanding the ground truth evaluation to 20–30 teams across multiple course offerings would provide stronger calibration data and reveal whether effort estimates remain consistent as course context and programming language vary. This larger sample would increase confidence in the CQI's reliability and identify model-specific or context-dependent biases.

== Platform Integration and Portability

Harmonia currently integrates exclusively with Artemis due to ITP's platform choice. Implementing GitLab and GitHub support would enable adoption in institutions using those platforms. Additionally, defining a standardized export format for CQI scores and collaboration evidence would facilitate integration with other grading systems and research tools, reducing dependence on platform-specific implementations.

== Feedback System Instrumentation

The student feedback system currently operates independently from Harmonia. Instrumenting the IDE to log when students request feedback and correlating these requests with team CQI scores could reveal behavioral patterns—for instance, whether teams with imbalanced collaboration proactively seek more guidance or remain unaware of their collaboration issues. This instrumentation would provide insight into how students respond to collaboration imbalance.

== Adversarial Robustness

An important open question is whether students can deliberately game the CQI through strategic commit practices, such as splitting a single feature into many small commits or padding commit messages with inflated effort claims. Investigating these edge cases is necessary for assessment validity. Detection mechanisms based on code diff size, semantic consistency of commit messages, or statistical anomaly detection could harden the metric against manipulation.

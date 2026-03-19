= Conclusion

This paper presented Harmonia, a system for automated assessment of student collaboration in team software projects. The core innovation is the Collaboration Quality Index (CQI) - a composite metric combining LLM rated effort (55%), lines of code balance (25%), temporal spread (5%), and file ownership (15%) - that moves beyond commit counts to assess the semantic quality of contributions.

Deployment in ITP for winter semester 2025/26 provided practical validation. Manual evaluation of five teams showed inter rater reliability above 70% on effort, complexity, and novelty dimensions, indicating that human and LLM assessments operate within a comparable range of variability. Team A1 achieved 99% agreement between independent reviewers, suggesting well structured commits are reliably assessable. Team H's 70% agreement (the lowest) highlighted inherent subjectivity in complex work assessment.

The system exposes limitations that warrant acknowledgment. The CQI is a composite metric that can obscure qualitative differences; a high scoring team might have divided responsibilities despite balanced effort, and vice versa. The sequential AI analysis scales linearly with commits, requiring 24 hours for 200 teams—far exceeding the 15 minute target. Lastly, Git history captures authorship and timestamps but not actual time invested; commits pushed by one student may represent collaboration, and vice versa.

The student feedback system complements instructor assessment by delivering non graded, priority categorized feedback in real time. This removes grading anxiety and supports learning throughout development.

The two sided approach - summative assessment for instructors, formative guidance for students - demonstrates how LLM driven tools can support different stakeholders in team based courses. Whether effort estimation generalizes to other LLM backends, and how to detect intentional gaming of the metric, remain open questions.

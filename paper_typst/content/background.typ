= Background
This section introduces the course context, outlines how the student project grading works, and discusses the problems that arise from the current evaluation process.

== Introduction to Programming

Introduction to Programming (ITP) is a foundational course at the Technical University of Munich (TUM). Prof. Stephan Krusche teaches the course for students enrolled in the Management and Technology program. ITP serves as an entry point into computer science and covers the fundamental concepts of programming using the Java language. By the end of the course, students can solve small algorithmic problems and develop simple applications independently. The ITP curriculum places strong emphasis on practical implementation.

The final course grade allocates 30% to the development of a software project that teams complete over several weeks, typically from early December to the end of January. Students work in teams of two to design and implement a fully functional game @michaelsen2004tbl. The entire development process relies on Git, meaning that teams use version control to manage their code and document their progress. The Git repository therefore serves as the primary source of evidence for both the technical development and the collaboration between team members.

== Project Grading

The course evaluates the project according to multiple criteria, with a strong emphasis on teamwork. Both team members must contribute equally, each accounting for roughly 50% of meaningful commits and lines of code. Every individual must also produce at least ten commits over the course of the project. The course enforces mandatory pair programming sessions to verify collaboration. Students must attend at least two of the three tutorial sessions held in January, during which they alternate between the roles of driver and navigator.

The driver writes the code on their own machine and pushes it from their own Artemis account, while the navigator guides the implementation and reviews the work. Beyond balanced contributions, the quality of the Git history plays an important role. Students must commit regularly throughout the entire project rather than pushing all their work near the deadline. Each commit should remain small in scope and carry a clear message, as vague or generic descriptions may result in point deductions. Students must use Artemis Git exclusively, since the course instructors can only access and review commits made through this platform. Both team members also present their project during a final tutorial session, where they demonstrate their understanding of the full codebase and the design decisions they made.

== Problems

The current evaluation process involves multiple roles, each with limited visibility into the full picture. Students work within their tutorial groups under the supervision of a tutor. The tutor observes pair programming sessions, verifies collaboration, and provides feedback to the course instructor. The instructor, however, does not directly observe the students and must rely on the assessments of individual tutors. This chain of trust introduces subjectivity, as different tutors may apply the grading criteria with varying levels of strictness @kopec2023impact.

Evaluating student teams at the scale of around 200 teams strains the instructor's capacity. Assessing contribution balance, commit quality, and pair programming compliance requires manually inspecting each team's Git history, a process that consumes considerable time and produces inconsistent results. The evaluation often lacks transparency, and students receive limited feedback on how the instructor judged their collaboration.

No centralized overview allows the instructor to compare teams across the entire course, and each team's repository requires individual examination. Spotting problematic teams, such as those with heavily imbalanced contributions or last-minute bulk commits, becomes difficult without aggregated data. These limitations motivate the development of an automated, data-driven tool that aggregates collaboration metrics, provides transparent evidence, and offers a unified overview across all teams.

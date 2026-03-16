= Background
This section provides the necessary context for understanding the motivation behind Harmonia. It first introduces the course in which Harmonia is intended to be used, then outlines how the student project is graded, and finally discusses the key problems that arise from the current evaluation process.

== Introduction to Programming

Introduction to Programming (ITP) is a foundational course at the Technical University of Munich (TUM). It is taught by Prof. Stephan Krusche and is designed for students enrolled in the Management and Technology program. The course serves as an entry point into computer science and focuses on the fundamental concepts of programming using the Java language. By the end of the course, students are expected to be able to solve small algorithmic problems and develop simple applications independently.

Practical implementation plays a central role in the ITP curriculum. As a result, 30% of the final course grade is based on the development of a software project completed over several weeks, typically from early December to the end of January. During this project, students work in teams of two to design and implement a fully functional game. The entire development process is Git-based, meaning that teams must use version control to manage their code and document their progress. Therefore, the Git repository serves as the primary source of evidence for both the technical development and the collaboration between team members.

== Project Grading

The project is evaluated according to multiple criteria, with a strong emphasis on teamwork. Both team members are expected to contribute equally, each accounting for roughly 50% of meaningful commits and lines of code. Moreover, every individual must produce at least ten commits over the course of the project.

Collaboration is also assessed through mandatory pair programming sessions. Students must attend at least two of the three tutorial sessions held in January, and during these sessions, they alternate between the roles of driver and navigator. The driver writes the code on their own machine and pushes it from their own Artemis account, while the navigator guides the implementation and reviews the work.

Beyond balanced contributions, the quality of the Git history plays an important role as well. Students are expected to commit regularly throughout the entire project rather than pushing all their work near the deadline. Each commit should be small in scope and accompanied by a clear message. Vague or generic descriptions are considered poor practice and may result in point deductions. Notably, students are required to use Artemis Git exclusively, as it is the only platform accessible to course instructors. Consequently, only commits made through Artemis can be reviewed and considered during the grading process.

Finally, both team members must present their project during a final tutorial session. In this presentation, they need to demonstrate a solid understanding of the full codebase and the design decisions they made.

== Problems

The current evaluation process involves multiple roles, each with limited visibility into the full picture. Students work within their tutorial groups under the supervision of a tutor. The tutor is responsible for observing pair programming sessions, verifying collaboration, and providing feedback to the course instructor. However, the instructor does not directly observe the students and must therefore rely on the assessments made by individual tutors. This chain of trust introduces a degree of subjectivity, as different tutors may apply the grading criteria with varying levels of strictness.

From the instructor's perspective, evaluating student teams at scale presents a significant challenge. Assessing contribution balance, commit quality, and pair programming compliance requires manually inspecting each team's Git history. With a typical course size of around 200 teams, this process is both time-consuming and difficult to carry out consistently. As a result, the evaluation often lacks transparency, and students receive limited feedback on how their collaboration was assessed.

Furthermore, there is no centralized overview that allows the instructor to compare teams across the entire course. Each team's repository must be examined individually, making it difficult to spot problematic teams such as those with heavily imbalanced contributions or last-minute bulk commits.

These limitations highlight the need for an automated and data-driven tool. Such a tool should aggregate collaboration metrics, provide transparent evidence, and offer a comprehensive overview across all teams.

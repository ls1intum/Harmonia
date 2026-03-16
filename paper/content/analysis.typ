= Evaluation of Harmonia
This section evaluates whether Harmonia produces meaningful and accurate collaboration assessments. We first describe the setup of our manual evaluation, then present the results, and finally discuss the limitations of our approach.

== Set Up 
To evaluate the effectiveness of Harmonia, we conducted a manual analysis of five student teams from the ITP course of the winter semester 2025/26. The goal was to compare Harmonia's automated assessments with human judgment.

We selected the five teams to represent a diverse range of outcomes. One team received the highest overall score from Harmonia. Two teams received scores close to the course average. One team failed the project entirely. The last team received the lowest Harmonia score while still successfully completing the project. This selection ensures that the evaluation covers a broad spectrum of collaboration patterns.

For each team, two independent reviewers manually examined every Git commit from both team members. Each commit was classified into one of four categories: feature implementation, bug fix, refactoring, or trivial change. In addition, each commit was rated on three dimensions using a scale from 0.0 to 10.0.

The first dimension is effort, which captures the amount of meaningful work in a commit. Higher values indicate more substantial contributions. The second dimension is complexity, which reflects the technical difficulty of the code changes. Higher scores point to more complex logic or architectural decisions. The third dimension is novelty, which measures how much original code was introduced rather than boilerplate or duplicated content.

By having two reviewers independently assess each team, we reduce the risk of individual bias and increase the reliability of the manual evaluation. The results of this manual analysis serve as a ground truth against which Harmonia's automated scores can be compared.

== Results
- graphs

== Limitations
- menschen auch nach lust und laune bewerten 
- ki auch schlecht sein könnte mit einem schlechten modell



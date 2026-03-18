= Evaluation of Harmonia
This section evaluates whether Harmonia produces meaningful and accurate collaboration assessments. We first describe the setup of our manual evaluation, then present the results, and finally discuss the limitations of our approach.

== Set Up 
For the analysis of Harmonia, it was deployed on a production server and used the GPT-5-Mini model for its automated commit analysis. The tool computes a Collaboration Quality Index (CQI) for each team, which ranges from 0 to 100. A higher score indicates better overall collaboration (see Chapter 4.3 Collaboration Quality Index).

To evaluate the effectiveness of Harmonia, we conducted a manual analysis of five student teams from the ITP course of the winter semester 2025/26. The goal was to compare Harmonia's automated assessments with human judgment.

We selected five teams to represent a diverse range of outcomes. To preserve anonymity, we refer to them using pseudonyms. Team H received the highest CQI = 98. Team A1 and Team A2 scored 92 and 94, respectively, representing average performance. Team L achieved the lowest passing score of 73. Team F failed the project with a CQI = 4, as one team member did not contribute enough commits (see Chapter 2.2 Project Grading). @tbl-teams provides an overview of the five selected teams and their CQI scores.

#figure(
  table(
    columns: (auto, auto, auto),
    align: (left, center, left),
    [*Team*], [*CQI Score*], [*Description*],
    [Team H], [98], [Highest score],
    [Team A1], [92], [Average score],
    [Team A2], [94], [Average score],
    [Team L], [73], [Lowest passing score],
    [Team F], [4], [Failed],
  ),
  caption: [Overview of the five evaluated teams and their CQI scores.],
) <tbl-teams>

------ TODO LATER ADD WHEN HARMONIA IS DONE + CHANGE CHARTS ------

Harmonia was unable to fully analyze Team F due to insufficient commit data, so no AI-generated scores are available for this team. This selection ensures that the evaluation covers a broad spectrum of collaboration patterns.

------------


For each team, two independent reviewers manually assessed every Git commit from both team members. Each commit was classified into one of four categories: Feature, Bug Fix, Refactor, or Trivial. In addition, each commit was rated on three dimensions using a scale from 0.0 to 10.0.

The first dimension is "Effort", which captures the amount of meaningful work in a commit. Higher values indicate more substantial contributions. The second dimension is "Complexity", which reflects the technical difficulty of the code changes. Higher scores point to more complex logic or architectural decisions. The third dimension is "Novelty", which measures how much original code was introduced rather than boilerplate or duplicated content.

By conducting two independent assessments of each team, we reduce the risk of individual bias and increase the reliability of the manual evaluation. The results of the manual analysis serve as a ground truth against which Harmonia's automated scores can be compared.

== Results

To assess the reliability of the manual evaluation, we first measured the agreement between the two independent human reviewers. The correlation was calculated using the Pearson correlation coefficient, which measures the linear relationship between two sets of scores on a scale from 0% to 100%. For "Category" agreement, we computed the percentage of commits where both reviewers assigned the same category. @fig-irr shows the results across all five teams.

#figure(
image("../figures/01_inter_rater_combined.png", width: 95%),
caption: [Correlation between two manual assessments across all five teams.],
) <fig-irr>

The grey dashed line marks the 70% threshold, which is commonly considered the boundary for a strong positive correlation. Most values across all teams and dimensions lie above this line. Team A1 stands out with near-perfect agreement, reaching 98% for "Effort", 98% for "Complexity", and 99% for "Novelty". "Category" agreement for this team is also the highest at 93%. Possible reasons for this unusually high agreement are discussed in Section 5.3.

Looking at the three score dimensions ("Effort", "Complexity", and "Novelty"), Team H shows the largest disagreement between the two reviewers, with an average correlation of 70%. Its "Complexity" score of 67% is the lowest single value across all teams. Team A2 follows with an average of 74%, where "Effort" drops to 70%. In contrast, Team F and Team L both show stable agreement above 72% across all three dimensions.

Because Team H and Team A1 represent the two most contrasting cases, the lowest and highest inter-rater agreement respectively, we selected these two teams for a more detailed per-commit comparison with Harmonia later in this section (see *TODO fig-teamh and TODO fig-teama1*).




== Limitations
- menschen auch nach lust und laune bewerten 
- ki auch schlecht sein könnte mit einem schlechten modell

Figure 1:
It is not entirely clear why Team A1 shows such a high level of agreement between the two reviewers. One possible explanation is that the commits themselves were particularly clear and well-structured, making them easier to assess consistently. Alternatively, the high agreement could be a coincidence, or one reviewer may have been unintentionally influenced by the other. It is also possible that the team followed a very consistent development pattern, resulting in commits that were straightforward to categorize and score.



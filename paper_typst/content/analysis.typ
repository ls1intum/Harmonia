= Evaluation of Harmonia
This section evaluates whether Harmonia produces meaningful and accurate collaboration assessments. We first describe the setup of our manual evaluation, then present the results, and discuss the limitations of our approach.

== Set Up
We deployed Harmonia on a production server and configured it with the GPT-5-Mini model for automated commit analysis. The tool computes a Collaboration Quality Index (CQI) for each team, ranging from 0 to 100, where a higher score indicates better overall collaboration (see @cqi). We selected five student teams from the ITP course of the winter semester 2025/26 to compare Harmonia's automated assessments with human judgment. The five teams represent a diverse range of outcomes, and we refer to them using pseudonyms to preserve anonymity.

Team H received the highest CQI of 98. Team A1 and Team A2 scored 92 and 94, representing average performance. Team L achieved the lowest passing score of 73. Team F failed the project because one team member did not contribute enough commits (see Section 2.2). Normally, Harmonia does not compute a CQI for teams that fail hard requirements such as the minimum commit count, but for this evaluation we manually requested an AI analysis, which resulted in a CQI of 92. @tbl-teams provides an overview of the five selected teams and their CQI scores.
#figure(
  table(
    columns: (auto, auto, auto),
    stroke: 0.6pt + black,
    align: (left, center, left),
    table.header(
      [*Team*], [*CQI Score*], [*Description*],
    ),
    [Team H], [98], [Highest score],
    [Team A1], [92], [Average score],
    [Team A2], [94], [Average score],
    [Team L], [73], [Lowest passing score],
    [Team F], [92], [Failed (manually analyzed)],
  ),
  caption: [Overview of the five evaluated teams and their CQI scores.],
) <tbl-teams>

Two independent reviewers manually assessed every Git commit from both team members of each team. Each commit received a classification into one of four categories: Feature, Bug Fix, Refactor, or Trivial. The reviewers also rated each commit on three dimensions using a scale from 0.0 to 10.0: "Effort" captures the amount of meaningful work, "Complexity" reflects the technical difficulty of the code changes, and "Novelty" measures how much original code the commit introduced rather than boilerplate or duplicated content. Conducting two independent assessments of each team reduces the risk of individual bias and increases the reliability of the manual evaluation.

== Results
We first measured the agreement between the two independent human reviewers using the Pearson correlation coefficient, which captures the linear relationship between two sets of scores on a scale from 0% to 100%. For "Category" agreement, we computed the percentage of commits where both reviewers assigned the same category. @fig-irr shows the results across all five teams.

#figure(
image("../figures/01_inter_rater_combined.png", width: 95%),
caption: [Correlation between two manual assessments across all five teams.],
) <fig-irr>

The grey dashed line marks the 70% threshold, commonly regarded as the boundary for a strong positive correlation. Most values across all teams and dimensions lie above this line. Team A1 stands out with near-perfect agreement, reaching 98% for "Effort", 98% for "Complexity", and 99% for "Novelty", with a "Category" agreement of 93%. Team H shows the largest disagreement between the two reviewers, with an average correlation of 70% and a "Complexity" score of 67%, the lowest single value across all teams. Team A2 follows with an average of 72%, where "Effort" drops to 68%, while Team F and Team L both show stable agreement above 72% across all three dimensions.

We selected Team H and Team A1 for a more detailed per-commit comparison with Harmonia later in this section (see @fig-teamh and @fig-teama1) because they represent the two most contrasting cases of inter-rater agreement. @fig-irr confirms that the manual evaluation remains sufficiently consistent across both reviewers to serve as a reliable baseline for evaluating Harmonia.

We then compared the average scores assigned by human reviewers with those produced by Harmonia. @fig-scores shows the mean "Effort", "Complexity", and "Novelty" scores per team.

#figure(
image("../figures/02_manual_vs_harmonia_scores.png", width: 95%),
caption: [Average "Effort", "Complexity", and "Novelty" scores per team as assessed by manual reviewers and Harmonia.],
) <fig-scores>

For "Effort", Harmonia consistently assigns lower scores than the human reviewers. The gap remains small for Team H (+0.5) and Team L (+0.2) but grows for Team F (+1.2), Team A1 (+1.6), and Team A2 (+2.0). This suggests that Harmonia underestimates the amount of meaningful work in a commit, particularly for teams with higher overall contribution quality. For "Complexity", the pattern reverses: Harmonia rates most teams higher than the human reviewers, with the largest difference for Team L (-0.9) and Team F (-0.8), while Team A1 shows near-identical assessments (-0.2). Only Team A2 receives a higher human assessment than Harmonia's (+0.6). For "Novelty", the two assessments align closely for Team H (difference of 0.0) and Team F (+0.3), but the difference widens for Team A1 (+0.9) and Team A2 (+1.5), following a similar pattern as "Effort".

@fig-categories shows the distribution of commit categories assigned by both the human reviewers and Harmonia.
#figure(
image("../figures/03_category_distribution.png", width: 100%),
caption: [Distribution of commit categories as classified by manual assessments and Harmonia.],
) <fig-categories>

Across all teams, Harmonia classifies a larger proportion of commits as "Feature" compared to the human reviewers. For Team H, the difference stands out most: humans assigned 48% of commits to "Feature", while Harmonia assigned 66%. A similar gap appears for Team L (58% vs. 72%) and Team F (63% vs. 71%). At the same time, Harmonia assigns far fewer commits to the "Trivial" category. In Team H, human reviewers classified 39% of commits as "Trivial", while Harmonia assigned only 13%. For Team A1, humans rated 18% of commits as "Trivial", but Harmonia assigned 0%. This pattern suggests that Harmonia overestimates the importance of smaller commits and rarely classifies a commit as low-effort.

For "Bug Fix", Harmonia consistently assigns higher percentages than the human reviewers, most noticeably for Team A2 (26% vs. 33%) and Team A1 (12% vs. 21%). "Refactor" shows a more mixed picture, with both assessments remaining relatively close across most teams. Harmonia appears to shift commits away from the "Trivial" category and distribute them primarily into "Feature" and "Bug Fix", a pattern consistent across all analyzed teams that should inform the interpretation of Harmonia's category-level output.

Before comparing Harmonia's scores at the individual commit level, @fig-manual-scores provides an overview of the manual score patterns per commit, averaged across both reviewers.

#figure(
image("../figures/04_score_lines_per_team.png", width: 100%),
caption: [Manual scores per commit, averaged across both reviewers, for all five teams.],
) <fig-manual-scores>

Across all teams, the scores fluctuate noticeably from commit to commit, with no team showing a consistent pattern. High-scoring commits frequently follow low-scoring ones, regardless of the team's overall performance. This variation does not depend on the total number of commits: Team F with only 20 commits shows a similar pattern to Team L with 77. The three dimensions remain closely aligned, with an average absolute difference of only 0.5 to 0.6 points, though Team F shows the most variation between dimensions at approximately 1.0 point.

We selected Team H and Team A1 for a detailed per-commit comparison with Harmonia. @fig-teamh shows the results for Team H, the team with the lowest inter-rater agreement.

#figure(
image("../figures/05_comparison_team_h.png", width: 100%),
caption: [Comparison of "Effort", "Complexity", and "Novelty" scores for Team H: two manual assessments vs. Harmonia.],
) <fig-teamh>

The two human reviewers follow similar trends but show visible disagreements, particularly in "Effort" and "Complexity", consistent with Team H's lower inter-rater reliability observed in @fig-irr. Harmonia's scores, shown in purple, never exceed 5.0 for any dimension, while the human reviewers use the full range from 0.0 to 10.0 with several commits rated above 8.0. Despite this compressed range, Harmonia generally follows the same ups and downs as the human reviewers: when both humans rate a commit high, Harmonia tends to rate it higher as well, though the absolute values remain much lower. @fig-teama1 shows the same comparison for Team A1, the team with the highest inter-rater agreement.

#figure(
image("../figures/05_comparison_team_a1.png", width: 100%),
caption: [Comparison of "Effort", "Complexity", and "Novelty" scores for Team A1: two manual assessments vs. Harmonia.],
) <fig-teama1>

For Team A1, the two human reviewers produce nearly identical scores across all three dimensions, confirming the high inter-rater reliability. Harmonia again stays within a narrow range, with "Effort" never exceeding 4.5 while human reviewers regularly assign scores of 8.0 or above. The gap between human and AI scores grows more pronounced for this team than for Team H, which aligns with the higher manual averages observed in @fig-scores. As with Team H, Harmonia captures the relative pattern but compresses all values into roughly the lower half of the scale.

== Limitations
The evaluation carries several limitations. The sample of only five teams restricts the generalizability of the findings. While we selected these teams to cover a range of outcomes, a larger evaluation would strengthen the conclusions about Harmonia's accuracy. The manual assessment itself introduces subjectivity, as reviewers may rate commits differently depending on their interpretation of the scoring criteria, their familiarity with the codebase, or their level of concentration.

The inter-rater reliability scores range from 68% to 99% depending on the team and dimension, and the unusually high agreement for Team A1 remains difficult to explain, possibly because the commits were particularly clear, the agreement was coincidental, or one reviewer was unintentionally influenced by the other. Human reviewers may also carry bias from the commit message itself. A longer or more detailed message could lead to a higher "Effort" score, while a short message may result in a lower rating regardless of the actual code changes. If a commit message explicitly mentions a bug fix, the reviewer may classify it as "Bug Fix" without thoroughly inspecting the code. Reviewers may also adjust their scores based on whether they suspect the code was generated by an AI tool such as ChatGPT, Claude, or GitHub Copilot, unconsciously assigning a lower "Novelty" or "Effort" score even when the actual contribution is meaningful.

Students sometimes combined multiple types of work in a single commit, such as refactoring existing code while also adding a new feature. Assigning a single category in these cases is ambiguous, and different reviewers may prioritize different aspects of the same commit. More broadly, no objectively correct category or score exists for any given commit: both the human and AI classifications are interpretations, and when Harmonia disagrees with the human reviewers, this does not necessarily mean the AI is wrong but may reflect a different but equally valid perspective.

On the AI side, Harmonia consistently operates within a compressed scoring range of roughly 0.5 to 5.0, while human reviewers use the full scale from 0.0 to 10.0. This compression makes direct comparison of absolute scores difficult, even when the relative patterns align. Harmonia also tends to underclassify "Trivial" commits, shifting them primarily into the "Feature" and "Bug Fix" categories. The results depend on the specific model used: this evaluation used GPT-5-Mini, and a different or more capable model could produce different scores and classifications.

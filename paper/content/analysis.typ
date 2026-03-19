= Evaluation of Harmonia
This section evaluates whether Harmonia produces meaningful and accurate collaboration assessments. We first describe the setup of our manual evaluation, then present the results, and finally discuss the limitations of our approach.

== Set Up 
For the analysis of Harmonia, it was deployed on a production server and used the GPT-5-Mini model for its automated commit analysis. The tool computes a Collaboration Quality Index (CQI) for each team, which ranges from 0 to 100. A higher score indicates better overall collaboration (see Chapter 4.3 Collaboration Quality Index).

To evaluate the effectiveness of Harmonia, we conducted a manual analysis of five student teams from the ITP course of the winter semester 2025/26. The goal was to compare Harmonia's automated assessments with human judgment.

We selected five teams to represent a diverse range of outcomes. To preserve anonymity, we refer to them using pseudonyms. Team H received the highest CQI = 98. Team A1 and Team A2 scored 92 and 94, respectively, representing average performance. Team L achieved the lowest passing score of 73. Team F failed the project, as one team member did not contribute enough commits (see Chapter 2.2 Project Grading). Normally, Harmonia does not compute a CQI for teams that fail hard requirements such as the minimum commit count. However, for the purpose of this evaluation, we manually requested an AI analysis, which resulted in a CQI = 92. @tbl-teams provides an overview of the five selected teams and their CQI scores. This selection ensures that the evaluation covers a broad spectrum of collaboration patterns.
#figure(
  table(
    columns: (auto, auto, auto),
    align: (left, center, left),
    [*Team*], [*CQI Score*], [*Description*],
    [Team H], [98], [Highest score],
    [Team A1], [92], [Average score],
    [Team A2], [94], [Average score],
    [Team L], [73], [Lowest passing score],
    [Team F], [92], [Failed (manually analyzed)],
  ),
  caption: [Overview of the five evaluated teams and their CQI scores.],
) <tbl-teams>

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

Looking at the three score dimensions ("Effort", "Complexity", and "Novelty"), Team H shows the largest disagreement between the two reviewers, with an average correlation of 70%. Its "Complexity" score of 67% is the lowest single value across all teams. Team A2 follows with an average of 72%, where "Effort" drops to 68%. In contrast, Team F and Team L both show stable agreement above 72% across all three dimensions.

Because Team H and Team A1 represent the two most contrasting cases, the lowest and highest inter-rater agreement respectively, we selected these two teams for a more detailed per-commit comparison with Harmonia later in this section (see @fig-teamh and @fig-teama1).

Overall @fig-irr shows that the manual evaluation is sufficiently consistent across both reviewers to serve as a reliable ground truth for evaluating Harmonia.



Secondly, we compared the average scores assigned by human reviewers with those produced by Harmonia. @fig-scores shows the mean "Effort", "Complexity", and "Novelty" scores per team.

#figure(
image("../figures/02_manual_vs_harmonia_scores.png", width: 95%),
caption: [Average "Effort", "Complexity", and "Novelty" scores per team as assessed by manual assessments and Harmonia.],
) <fig-scores>

For "Effort", Harmonia consistently assigns lower scores than the human reviewers. The gap is small for Team H (+0.5) and Team L (+0.2) but grows for Team F (+1.2), Team A1 (+1.6), and Team A2 (+2.0). This suggests that Harmonia tends to underestimate the amount of meaningful work in a commit, particularly for teams with higher overall contribution quality.

For "Complexity", the pattern is reversed. Harmonia rates most teams higher than the human reviewers, with the largest difference for Team L (-0.9) and Team F (-0.8). Team H shows a smaller gap of -0.5. For Team A1, both assessments are nearly identical (-0.2). Only for Team A2 does the human assessment exceed Harmonia's (+0.6). This indicates that Harmonia perceives more technical difficulty in commits than human reviewers do.

For "Novelty", the two assessments are very close for Team H (difference of 0.0) and Team F (+0.3). Team L also shows a small gap (-0.2). However, the difference widens for Team A1 (+0.9) and Team A2 (+1.5), following a similar pattern as "Effort".



@fig-categories shows the distribution of commit categories assigned by both the human reviewers and Harmonia.
#figure(
image("../figures/03_category_distribution.png", width: 100%),
caption: [Distribution of commit categories as classified by manual assessments and Harmonia.],
) <fig-categories>

Across all teams, a consistent pattern emerges. Harmonia classifies a larger proportion of commits as "Feature" compared to the human reviewers. For Team H, the difference is most pronounced: humans assigned 48% of commits to "Feature", while Harmonia assigned 66%. A similar gap is visible for Team L (58% vs. 72%) and Team F (63% vs. 71%).

At the same time, Harmonia assigns far fewer commits to the "Trivial" category. In Team H, human reviewers classified 39% of commits as "Trivial", while Harmonia assigned only 13%. For Team F, the gap is also significant (23% vs. 4%). For Team A1, the difference is even more striking: humans rated 18% of commits as "Trivial", but Harmonia assigned 0%. This suggests that Harmonia tends to overestimate the significance of smaller commits and rarely classifies a commit as low-effort.

For "Bug Fix", Harmonia consistently assigns higher percentages than the human reviewers. The difference is moderate for most teams but more noticeable for Team A2 (26% vs. 33%) and Team A1 (12% vs. 21%). "Refactor" shows a more mixed picture, with both assessments being relatively close across most teams.

In general, Harmonia appears to shift commits away from the "Trivial" category and distribute them primarily into "Feature" and "Bug Fix". This pattern is consistent across all analyzed teams and should be considered when interpreting Harmonia's category-level output.




Before comparing Harmonia's scores at the individual commit level, @fig-manual-scores provides an overview of the manual score patterns per commit, averaged across both reviewers.

#figure(
image("../figures/04_score_lines_per_team.png", width: 100%),
caption: [Manual scores per commit, averaged across both reviewers, for all five teams.],
) <fig-manual-scores>

Across all teams, the scores fluctuate significantly from commit to commit. No team shows a consistent pattern. High-scoring commits are frequently followed by low-scoring ones, regardless of the team's overall performance. This variation is also independent of the total number of commits: Team F with only 20 commits shows a similar pattern to Team L with 77.

The three dimensions are closely aligned, with an average absolute difference of only 0.5 to 0.6 points. Team F shows the most variation between dimensions, with an average difference of approximately 1.0 point.


As mentioned earlier, we selected Team H and Team A1 for a detailed per-commit comparison with Harmonia. @fig-teamh shows the results for Team H, the team with the lowest inter-rater agreement.

#figure(
image("../figures/05_comparison_team_h.png", width: 100%),
caption: [Comparison of "Effort", "Complexity", and "Novelty" scores for Team H: two manual assessments vs. Harmonia.],
) <fig-teamh>

The two human reviewers follow similar trends but show visible disagreements, particularly in "Effort" and "Complexity". This is consistent with Team H's lower inter-rater reliability observed in @fig-irr. Harmonia's scores, shown in purple, never exceed 5.0 for any dimension. In contrast, the human reviewers use the full range from 0.0 to 10.0, with several commits rated above 8.0. Despite this compressed range, Harmonia generally follows the same ups and downs as the human reviewers. When both humans rate a commit high, Harmonia tends to rate it higher as well, though the absolute values remain much lower.


@fig-teama1 shows the same comparison for Team A1, the team with the highest inter-rater agreement.

#figure(
image("../figures/05_comparison_team_a1.png", width: 100%),
caption: [Comparison of "Effort", "Complexity", and "Novelty" scores for Team A1: two manual assessments vs. Harmonia.],
) <fig-teama1>

For Team A1, the two human reviewers are nearly identical across all three dimensions, confirming the high inter-rater reliability. Harmonia again stays within a narrow range, with "Effort" never exceeding 4.5 while human reviewers regularly assign scores of 8.0 or above. The gap between human and AI scores is more pronounced for this team than for Team H, which aligns with the higher manual averages observed in @fig-scores. As with Team H, Harmonia captures the relative pattern but compresses all values into roughly the lower half of the scale.


== Limitations
Several limitations should be considered when interpreting the results of this evaluation.

First, the evaluation is based on only five teams. While we selected these teams to cover a range of outcomes, this sample size limits the generalizability of our findings. A larger evaluation would be needed to draw stronger conclusions about Harmonia's accuracy.

Second, the manual assessment itself is inherently subjective. Reviewers may rate commits differently depending on their personal interpretation of the scoring criteria, their familiarity with the codebase, or even their level of concentration at the time. This subjectivity is reflected in the inter-rater reliability scores, which range from 68% to 99% depending on the team and dimension. In particular, the unusually high agreement for Team A1 remains difficult to explain. Possible reasons include that the commits were particularly clear and straightforward, that the agreement was coincidental, or that one reviewer was unintentionally influenced by the other.

Third, human reviewers may be biased by the commit message itself. A longer or more detailed message could lead to a higher "Effort" score, while a short message may result in a lower rating regardless of the actual code changes. Similarly, if a commit message explicitly mentions a bug fix, the reviewer may classify it as "Bug Fix" without thoroughly inspecting the code.
Additionally, human reviewers may be influenced by their perception of whether the code was written by the student or generated by an AI tool such as ChatGPT, Claude, or GitHub Copilot. If a reviewer suspects that a commit contains AI-generated code, they might unconsciously assign a lower "Novelty" or "Effort" score, even if the actual contribution is meaningful.

Fourth, students sometimes combined multiple types of work in a single commit, such as refactoring existing code while also adding a new feature. In these cases, assigning a single category is inherently ambiguous. Different reviewers may prioritize different aspects of the same commit, which contributes to disagreement in category classification.
More broadly, there is no objectively correct category or score for any given commit. Both the human and AI classifications are interpretations. When Harmonia disagrees with the human reviewers, this does not necessarily mean the AI is wrong. It may simply reflect a different but equally valid perspective.

On the AI side, Harmonia consistently operates within a compressed scoring range of roughly 0.5 to 5.0, while human reviewers use the full scale from 0.0 to 10.0. This makes direct comparison of absolute scores difficult, even when the relative patterns align. Additionally, Harmonia shows a tendency to underclassify "Trivial" commits, shifting them primarily into the "Feature" and "Bug Fix" categories.

Finally, the results are dependent on the specific model used. This evaluation was conducted with gpt-5-mini. A different or more capable model could produce significantly different scores and classifications.


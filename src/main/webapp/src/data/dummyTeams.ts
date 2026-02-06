import type { Team } from '@/types/team';

export const dummyTeams: Team[] = [
  {
    id: 'team-1',
    teamName: 'Team Alpha',
    tutor: 'Dr. Smith',
    students: [
      { name: 'Emma Johnson', commitCount: 78, linesAdded: 1234 },
      { name: 'Michael Chen', commitCount: 72, linesAdded: 1186 },
    ],
    cqi: 87,
    isSuspicious: false,
    basicMetrics: {
      totalCommits: 150,
      totalLines: 2420,
    },
    subMetrics: [
      {
        name: 'Contribution Balance',
        value: 92,
        weight: 30,
        description: 'Are teammates contributing at similar levels?',
        details:
          'Both students show balanced commit patterns with Emma at 52% and Michael at 48% of total contributions. Similar lines added/removed ratio indicates equal effort distribution.',
      },
      {
        name: 'Pairing Signals',
        value: 85,
        weight: 30,
        description: 'Did teammates actually work together?',
        details:
          'Strong evidence of pair programming: 78% of commits have overlapping timestamps within 2 hours. Multiple co-authored commits detected. Frequent branch merges indicate collaborative workflow.',
      },
      {
        name: 'Ownership Distribution',
        value: 88,
        weight: 20,
        description: 'Are key files shared rather than monopolized?',
        details:
          'Critical files show balanced ownership: Main components have 60/40 split, core logic files have 55/45 distribution. No single file is exclusively owned by one team member.',
      },
      {
        name: 'Quality Hygiene at HEAD',
        value: 82,
        weight: 10,
        description: 'Basic quality standards met?',
        details:
          'Code passes linting checks, includes test coverage at 73%, documentation present for major components. Some minor warnings in dependency management.',
      },
    ],
  },
  {
    id: 'team-2',
    teamName: 'Team Beta',
    tutor: 'Dr. Smith',
    students: [
      { name: 'Sarah Williams', commitCount: 92, linesAdded: 1876 },
      { name: 'James Rodriguez', commitCount: 8, linesAdded: 142 },
    ],
    cqi: 45,
    isSuspicious: true,
    basicMetrics: {
      totalCommits: 100,
      totalLines: 2018,
    },
    subMetrics: [
      {
        name: 'Contribution Balance',
        value: 25,
        weight: 30,
        description: 'Are teammates contributing at similar levels?',
        details:
          'Significant imbalance detected: Sarah has 92% of all commits, James only 8%. Lines of code ratio is 94:6. One member appears to be carrying the project alone.',
      },
      {
        name: 'Pairing Signals',
        value: 15,
        weight: 30,
        description: 'Did teammates actually work together?',
        details:
          'Minimal collaboration evidence: Only 12% of commits have overlapping work periods. No co-authored commits. Work appears to be sequential rather than collaborative. Long gaps between contributions from second member.',
      },
      {
        name: 'Ownership Distribution',
        value: 68,
        weight: 20,
        description: 'Are key files shared rather than monopolized?',
        details:
          'Moderate file sharing: Some key files show 80/20 ownership split favoring Sarah. However, different components show clear ownership by each member, suggesting work division rather than collaboration.',
      },
      {
        name: 'Quality Hygiene at HEAD',
        value: 78,
        weight: 10,
        description: 'Basic quality standards met?',
        details:
          'Code meets basic standards with passing lints and 68% test coverage. Documentation is present but inconsistent in style. Some TODO comments remain unresolved.',
      },
    ],
  },
  {
    id: 'team-3',
    teamName: 'Team Gamma',
    tutor: 'Dr. Smith',
    students: [
      { name: 'Olivia Martinez', commitCount: 64, linesAdded: 1458 },
      { name: 'Daniel Kim', commitCount: 46, linesAdded: 1042 },
    ],
    cqi: 72,
    isSuspicious: false,
    basicMetrics: {
      totalCommits: 110,
      totalLines: 2500,
    },
    subMetrics: [
      {
        name: 'Contribution Balance',
        value: 78,
        weight: 30,
        description: 'Are teammates contributing at similar levels?',
        details:
          'Good balance overall: Olivia 58%, Daniel 42% of commits. The difference is within acceptable range. Both members contribute consistently throughout the project timeline.',
      },
      {
        name: 'Pairing Signals',
        value: 70,
        weight: 30,
        description: 'Did teammates actually work together?',
        details:
          'Moderate pairing evidence: 55% of work shows temporal overlap. Some co-authored commits present. Evidence of code reviews and pull request discussions indicate collaboration.',
      },
      {
        name: 'Ownership Distribution',
        value: 75,
        weight: 20,
        description: 'Are key files shared rather than monopolized?',
        details:
          'Reasonable file sharing: Most important files show 65/35 split. Each member has primary ownership of specific modules but both contribute to shared components.',
      },
      {
        name: 'Quality Hygiene at HEAD',
        value: 58,
        weight: 10,
        description: 'Basic quality standards met?',
        details:
          'Acceptable quality with some issues: Linting passes with warnings. Test coverage at 58% is below target. Documentation exists but needs improvement. Some code smells detected in recent commits.',
      },
    ],
  },
  {
    id: 'team-4',
    teamName: 'Team Delta',
    tutor: 'Dr. Smith',
    students: [
      { name: 'Ethan Brown', commitCount: 89, linesAdded: 1672 },
      { name: 'Sophia Lee', commitCount: 86, linesAdded: 1598 },
    ],
    cqi: 94,
    isSuspicious: false,
    basicMetrics: {
      totalCommits: 175,
      totalLines: 3270,
    },
    subMetrics: [
      {
        name: 'Contribution Balance',
        value: 96,
        weight: 30,
        description: 'Are teammates contributing at similar levels?',
        details:
          'Exceptional balance: Nearly perfect 51/49 split in commits and lines of code. Both members show consistent contribution patterns throughout all project phases. Equal participation in feature development.',
      },
      {
        name: 'Pairing Signals',
        value: 94,
        weight: 30,
        description: 'Did teammates actually work together?',
        details:
          'Excellent collaboration: 89% of work shows temporal overlap. Frequent co-authored commits. Evidence of active pair programming sessions. Regular real-time collaboration in multiple files.',
      },
      {
        name: 'Ownership Distribution',
        value: 92,
        weight: 20,
        description: 'Are key files shared rather than monopolized?',
        details:
          'Outstanding file sharing: All critical files show balanced contributions between 45-55%. No file monopolization detected. True collaborative ownership across the entire codebase.',
      },
      {
        name: 'Quality Hygiene at HEAD',
        value: 95,
        weight: 10,
        description: 'Basic quality standards met?',
        details:
          'Excellent quality standards: Clean code with zero linting errors. Test coverage at 91%. Comprehensive documentation. Proper dependency management. Best practices consistently followed.',
      },
    ],
  },
  {
    id: 'team-5',
    teamName: 'Team Epsilon',
    tutor: 'Dr. Smith',
    students: [
      { name: 'Ava Taylor', commitCount: 12, linesAdded: 234 },
      { name: 'Noah Anderson', commitCount: 68, linesAdded: 1546 },
    ],
    cqi: 38,
    isSuspicious: true,
    basicMetrics: {
      totalCommits: 80,
      totalLines: 1780,
    },
    subMetrics: [
      {
        name: 'Contribution Balance',
        value: 35,
        weight: 30,
        description: 'Are teammates contributing at similar levels?',
        details:
          'Poor balance: Noah contributes 85% of commits while Ava only 15%. Large disparity in code volume and frequency. One team member appears disengaged or overloaded.',
      },
      {
        name: 'Pairing Signals',
        value: 22,
        weight: 30,
        description: 'Did teammates actually work together?',
        details:
          'Very weak collaboration signals: Only 18% temporal overlap. No co-authored commits. Work appears completely divided with minimal interaction. No evidence of code reviews or discussions.',
      },
      {
        name: 'Ownership Distribution',
        value: 48,
        weight: 20,
        description: 'Are key files shared rather than monopolized?',
        details:
          "Poor file sharing: Most critical files show 90/10 split. Clear file monopolization by Noah. Ava's contributions are limited to a few isolated modules. Minimal cross-editing detected.",
      },
      {
        name: 'Quality Hygiene at HEAD',
        value: 62,
        weight: 10,
        description: 'Basic quality standards met?',
        details:
          'Below average quality: Multiple linting warnings. Test coverage at 45%. Incomplete documentation. Some deprecated dependencies. Code quality varies significantly between contributors.',
      },
    ],
  },
  {
    id: 'team-6',
    teamName: 'Team Zeta',
    tutor: 'Dr. Smith',
    students: [
      { name: 'Isabella Garcia', commitCount: 78, linesAdded: 1512 },
      { name: 'Liam Wilson', commitCount: 61, linesAdded: 1188 },
    ],
    cqi: 81,
    isSuspicious: false,
    basicMetrics: {
      totalCommits: 139,
      totalLines: 2700,
    },
    subMetrics: [
      {
        name: 'Contribution Balance',
        value: 84,
        weight: 30,
        description: 'Are teammates contributing at similar levels?',
        details:
          'Strong balance: Isabella 56%, Liam 44% of contributions. Both members maintain steady commit velocity. Difference is minimal and acceptable for team dynamics.',
      },
      {
        name: 'Pairing Signals',
        value: 82,
        weight: 30,
        description: 'Did teammates actually work together?',
        details:
          'Good collaboration evidence: 72% work overlap detected. Multiple co-authored commits. Active pull request discussions. Evidence of pair programming sessions and peer reviews.',
      },
      {
        name: 'Ownership Distribution',
        value: 79,
        weight: 20,
        description: 'Are key files shared rather than monopolized?',
        details:
          'Good file distribution: Key files show 62/38 average split. Both members contribute to critical components. Some specialization exists but shared ownership is maintained.',
      },
      {
        name: 'Quality Hygiene at HEAD',
        value: 76,
        weight: 10,
        description: 'Basic quality standards met?',
        details:
          'Good quality overall: Clean linting with minor warnings. Test coverage at 72%. Adequate documentation. Dependencies up to date. Few technical debt items remaining.',
      },
    ],
  },
];

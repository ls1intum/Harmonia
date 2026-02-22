import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import '@testing-library/jest-dom/vitest';
import TeamDetail from '../TeamDetail';
import type { Team, CourseAverages } from '@/types/team';

afterEach(cleanup);

const baseTeam: Team = {
  id: '1',
  teamName: 'Team Alpha',
  tutor: 'Dr. Smith',
  students: [
    { name: 'Alice', commitCount: 25, linesAdded: 100, linesDeleted: 20, linesChanged: 120 } as never,
    { name: 'Bob', commitCount: 30, linesAdded: 200, linesDeleted: 50, linesChanged: 250 } as never,
  ],
  analysisStatus: 'DONE',
  cqi: 75,
  basicMetrics: { totalCommits: 55, totalLines: 370 },
  isSuspicious: false,
};

const courseAverages: CourseAverages = {
  avgCQI: 68,
  avgCommits: 42,
  avgLines: 300,
  suspiciousPercentage: 10,
  totalTeams: 10,
  analyzedTeams: 8,
  gitAnalyzedTeams: 9,
};

const renderDetail = (props: { team?: Team; courseAverages?: CourseAverages | null }) =>
  render(
    <MemoryRouter>
      <TeamDetail team={props.team ?? baseTeam} onBack={() => {}} courseAverages={props.courseAverages} />
    </MemoryRouter>,
  );

describe('TeamDetail — Course Comparison card', () => {
  it('renders comparison card when courseAverages is provided', () => {
    renderDetail({ courseAverages });
    expect(screen.getByTestId('course-comparison-card')).toBeInTheDocument();
    expect(screen.getByText('Course Comparison')).toBeInTheDocument();
  });

  it('does not render comparison card when courseAverages is null', () => {
    renderDetail({ courseAverages: null });
    expect(screen.queryByTestId('course-comparison-card')).not.toBeInTheDocument();
  });

  it('does not render comparison card when courseAverages is not provided', () => {
    renderDetail({});
    expect(screen.queryByTestId('course-comparison-card')).not.toBeInTheDocument();
  });

  it('shows correct team vs average values', () => {
    renderDetail({ courseAverages });
    const card = screen.getByTestId('course-comparison-card');
    const cardScope = within(card);
    // Team commits
    expect(cardScope.getByText('55')).toBeInTheDocument();
    // Course avg commits
    expect(cardScope.getByText('Course avg: 42')).toBeInTheDocument();
    // Team lines
    expect(cardScope.getByText('370')).toBeInTheDocument();
    // Course avg lines
    expect(cardScope.getByText('Course avg: 300')).toBeInTheDocument();
    // Team CQI
    expect(cardScope.getByText('75')).toBeInTheDocument();
    // Course avg CQI
    expect(cardScope.getByText('Course avg: 68')).toBeInTheDocument();
  });

  it('shows analyzed teams count in subtitle', () => {
    renderDetail({ courseAverages });
    const card = screen.getByTestId('course-comparison-card');
    expect(within(card).getByText(/Based on 8 analyzed teams/)).toBeInTheDocument();
  });
});

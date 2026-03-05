import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@testing-library/jest-dom/vitest';
import CqiWeightsPanel from '@/components/CqiWeightsPanel';
import { cqiWeightsApi } from '@/lib/apiClient';

vi.mock('@/lib/apiClient', () => ({
  cqiWeightsApi: {
    getWeights: vi.fn(),
    saveWeights: vi.fn(),
    resetWeights: vi.fn(),
  },
}));

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

function createQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

const defaultWeights = {
  effortBalance: 0.55,
  locBalance: 0.25,
  temporalSpread: 0.05,
  ownershipSpread: 0.15,
  isDefault: true,
};

const customWeights = {
  effortBalance: 0.4,
  locBalance: 0.3,
  temporalSpread: 0.2,
  ownershipSpread: 0.1,
  isDefault: false,
};

function renderPanel(exerciseId = '42') {
  const queryClient = createQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <CqiWeightsPanel exerciseId={exerciseId} />
    </QueryClientProvider>,
  );
}

async function expandPanel() {
  await waitFor(() => {
    expect(screen.getByText('CQI Weights')).toBeInTheDocument();
  });
  fireEvent.click(screen.getByText('CQI Weights'));
}

describe('CqiWeightsPanel', () => {
  beforeEach(() => {
    vi.mocked(cqiWeightsApi.getWeights).mockResolvedValue({ data: defaultWeights } as any);
  });

  it('returns null while loading', () => {
    vi.mocked(cqiWeightsApi.getWeights).mockReturnValue(new Promise(() => {}));
    const { container } = renderPanel();
    expect(container.innerHTML).toBe('');
  });

  it('is collapsed by default and expands on click', async () => {
    renderPanel();
    await waitFor(() => {
      expect(screen.getByText('CQI Weights')).toBeInTheDocument();
    });
    expect(screen.queryByText('Effort Balance')).not.toBeInTheDocument();

    fireEvent.click(screen.getByText('CQI Weights'));
    expect(screen.getByText('Effort Balance')).toBeInTheDocument();
  });

  it('renders weight inputs after expanding', async () => {
    renderPanel();
    await expandPanel();
    expect(screen.getByText('Effort Balance')).toBeInTheDocument();
    expect(screen.getByText('LoC Balance')).toBeInTheDocument();
    expect(screen.getByText('Temporal Spread')).toBeInTheDocument();
    expect(screen.getByText('Ownership Spread')).toBeInTheDocument();
  });

  it('shows "Default" badge when isDefault is true', async () => {
    vi.mocked(cqiWeightsApi.getWeights).mockResolvedValue({ data: defaultWeights } as any);
    renderPanel();
    await waitFor(() => {
      expect(screen.getByText('Default')).toBeInTheDocument();
    });
  });

  it('shows "Custom" badge when isDefault is false', async () => {
    vi.mocked(cqiWeightsApi.getWeights).mockResolvedValue({ data: customWeights } as any);
    renderPanel();
    await waitFor(() => {
      expect(screen.getByText('Custom')).toBeInTheDocument();
    });
  });

  it('displays correct total and updates on input change', async () => {
    renderPanel();
    await expandPanel();

    expect(screen.getByText('Total: 100%')).toBeInTheDocument();

    const effortInput = screen.getByDisplayValue('55');
    fireEvent.change(effortInput, { target: { value: '50' } });

    expect(screen.getByText('Total: 95% (must equal 100%)')).toBeInTheDocument();
  });

  it('disables save button when total is not 100%', async () => {
    renderPanel();
    await expandPanel();

    const effortInput = screen.getByDisplayValue('55');
    fireEvent.change(effortInput, { target: { value: '50' } });

    expect(screen.getByText('Save')).toBeDisabled();
  });

  it('enables save button when total equals 100%', async () => {
    renderPanel();
    await expandPanel();
    expect(screen.getByText('Save')).not.toBeDisabled();
  });

  it('disables reset button when isDefault is true', async () => {
    vi.mocked(cqiWeightsApi.getWeights).mockResolvedValue({ data: defaultWeights } as any);
    renderPanel();
    await expandPanel();
    expect(screen.getByText('Reset')).toBeDisabled();
  });

  it('enables reset button when isDefault is false', async () => {
    vi.mocked(cqiWeightsApi.getWeights).mockResolvedValue({ data: customWeights } as any);
    renderPanel();
    await expandPanel();
    expect(screen.getByText('Reset')).not.toBeDisabled();
  });
});

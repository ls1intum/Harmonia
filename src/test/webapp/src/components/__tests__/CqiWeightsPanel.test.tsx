import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@testing-library/jest-dom/vitest';
import CqiWeightsPanel from '@/components/CqiWeightsPanel';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

function createQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function mockFetchResponse(data: unknown, ok = true, status = 200) {
  return vi.fn().mockResolvedValue({
    ok,
    status,
    json: () => Promise.resolve(data),
    text: () => Promise.resolve(typeof data === 'string' ? data : JSON.stringify(data)),
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

describe('CqiWeightsPanel', () => {
  beforeEach(() => {
    globalThis.fetch = mockFetchResponse(defaultWeights);
  });

  it('returns null while loading', () => {
    // Make fetch hang so the component stays in loading state
    globalThis.fetch = vi.fn().mockReturnValue(new Promise(() => {}));
    const { container } = renderPanel();
    expect(container.innerHTML).toBe('');
  });

  it('renders weight inputs after data loads', async () => {
    renderPanel();
    await waitFor(() => {
      expect(screen.getByText('CQI Weights')).toBeInTheDocument();
    });
    expect(screen.getByText('Effort Balance')).toBeInTheDocument();
    expect(screen.getByText('LoC Balance')).toBeInTheDocument();
    expect(screen.getByText('Temporal Spread')).toBeInTheDocument();
    expect(screen.getByText('Ownership Spread')).toBeInTheDocument();
  });

  it('shows "Default" badge when isDefault is true', async () => {
    globalThis.fetch = mockFetchResponse(defaultWeights);
    renderPanel();
    await waitFor(() => {
      expect(screen.getByText('Default')).toBeInTheDocument();
    });
  });

  it('shows "Custom" badge when isDefault is false', async () => {
    globalThis.fetch = mockFetchResponse(customWeights);
    renderPanel();
    await waitFor(() => {
      expect(screen.getByText('Custom')).toBeInTheDocument();
    });
  });

  it('displays correct total and updates on input change', async () => {
    renderPanel();
    await waitFor(() => {
      expect(screen.getByText('Total: 100%')).toBeInTheDocument();
    });

    const effortInput = screen.getByDisplayValue('55');
    fireEvent.change(effortInput, { target: { value: '50' } });

    expect(screen.getByText('Total: 95% (must equal 100%)')).toBeInTheDocument();
  });

  it('disables save button when total is not 100%', async () => {
    renderPanel();
    await waitFor(() => {
      expect(screen.getByText('Save')).toBeInTheDocument();
    });

    const effortInput = screen.getByDisplayValue('55');
    fireEvent.change(effortInput, { target: { value: '50' } });

    expect(screen.getByText('Save')).toBeDisabled();
  });

  it('enables save button when total equals 100%', async () => {
    renderPanel();
    await waitFor(() => {
      expect(screen.getByText('Save')).not.toBeDisabled();
    });
  });

  it('disables reset button when isDefault is true', async () => {
    globalThis.fetch = mockFetchResponse(defaultWeights);
    renderPanel();
    await waitFor(() => {
      expect(screen.getByText('Reset')).toBeDisabled();
    });
  });

  it('enables reset button when isDefault is false', async () => {
    globalThis.fetch = mockFetchResponse(customWeights);
    renderPanel();
    await waitFor(() => {
      expect(screen.getByText('Reset')).not.toBeDisabled();
    });
  });
});

export const DEV_MODE_STORAGE_KEY = 'harmonia.devMode';

export function readDevModeFromStorage(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }

  try {
    return window.localStorage.getItem(DEV_MODE_STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
}

export function writeDevModeToStorage(enabled: boolean): void {
  if (typeof window === 'undefined') {
    return;
  }

  try {
    window.localStorage.setItem(DEV_MODE_STORAGE_KEY, String(enabled));
  } catch {
    // Ignore storage errors and keep in-memory UI state.
  }
}

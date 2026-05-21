/**
 * Best-effort wrappers around the {@link Storage} API.
 *
 * <p>PAT-149 — Safari Private mode and quota-exceeded scenarios make {@code
 * setItem} / {@code removeItem} throw synchronously. Callers (Redux slices,
 * Context providers, draft helpers) almost never want a storage hiccup to
 * propagate as an unhandled exception that crashes a reducer or Suspense
 * boundary; the consequence of a missed write is at worst "user has to log in
 * again on next refresh," which is strictly better than a white screen.
 *
 * <p>{@code getItem} on every browser we target either returns {@code null}
 * (key missing) or the value — the spec doesn't require it to throw — so the
 * read wrapper is mostly here for symmetry and to handle the rare
 * disabled-storage edge case (older Firefox in strict mode disables {@code
 * window.localStorage} entirely → {@code TypeError}).
 */
function safeGet(storage: Storage, key: string): string | null {
  try {
    return storage.getItem(key)
  } catch {
    return null
  }
}

function safeSet(storage: Storage, key: string, value: string): boolean {
  try {
    storage.setItem(key, value)
    return true
  } catch {
    return false
  }
}

function safeRemove(storage: Storage, key: string): void {
  try {
    storage.removeItem(key)
  } catch {
    // best-effort
  }
}

export const safeLocalStorage = {
  getItem: (key: string) => safeGet(localStorage, key),
  setItem: (key: string, value: string) => safeSet(localStorage, key, value),
  removeItem: (key: string) => safeRemove(localStorage, key),
}

export const safeSessionStorage = {
  getItem: (key: string) => safeGet(sessionStorage, key),
  setItem: (key: string, value: string) => safeSet(sessionStorage, key, value),
  removeItem: (key: string) => safeRemove(sessionStorage, key),
}

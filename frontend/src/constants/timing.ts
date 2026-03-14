/**
 * Centralized timing constants for debounce, auto-dismiss, and delays.
 */

/** Debounce delay for CQL autosave to localStorage (ms) */
export const AUTOSAVE_DEBOUNCE_MS = 5000

/** Auto-dismiss delay for success/info alerts (ms) */
export const ALERT_DISMISS_MS = 5000

/** Auto-dismiss delay for error alerts (ms) */
export const ALERT_DISMISS_ERROR_MS = 8000

/** Debounce delay for code/terminology search inputs (ms) */
export const SEARCH_DEBOUNCE_CODE_MS = 500

/** Debounce delay for general search/filter inputs (ms) */
export const SEARCH_DEBOUNCE_GENERAL_MS = 300

/** Polling interval for async operations like bulk export (ms) */
export const POLL_INTERVAL_MS = 5000

/** Timeout for copy-to-clipboard feedback (ms) */
export const COPY_FEEDBACK_TIMEOUT_MS = 2000

/** Debounce for fast autosave (JSON sync, draft save) (ms) */
export const AUTOSAVE_FAST_MS = 500

/** Debounce for normal autosave (localStorage persist) (ms) */
export const AUTOSAVE_NORMAL_MS = 1000

/** Debounce for slow/expensive operations (CQL parse, artifact save) (ms) */
export const AUTOSAVE_SLOW_MS = 1500

/** Debounce for very slow operations (CQL structure analysis) (ms) */
export const AUTOSAVE_HEAVY_MS = 2000

/** Default notification auto-dismiss duration (ms) */
export const NOTIFICATION_DURATION_MS = 4000

/** Delay for UI state reset transitions (ms) */
export const UI_TRANSITION_DELAY_MS = 300

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

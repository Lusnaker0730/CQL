/**
 * Score / threshold formatting helpers for the dashboard family of components.
 *
 * <p>PAT-151 — single source of truth for the rule "what unit + how many
 * decimal places does this number get rendered as?". Previously the
 * proportion-only assumption was hard-coded in {@code QualityReportPanel} /
 * {@code ThresholdAlertPanel} (always appended {@code "%"}), which was wrong
 * for continuous-variable thresholds (HbA1c < 7.0 mmol/L would show as
 * "5.6% < 7%") and cohort counts (raw integers). PAT-124 introduced
 * {@code scoringFamily} for the chart side; this utility extends the same
 * rule to numeric panels.
 */

import { classifyScoring, type ScoringFamily } from '../components/dashboard/scoringFamily'

interface FormatOptions {
  scoringType?: string
  unit?: string
  /** Returned when {@code value} is null / undefined. Defaults to {@code "-"}. */
  naLabel?: string
}

/**
 * Format a score value (e.g. an actual measurement, an average, a target).
 *
 * <ul>
 *   <li>{@code proportion} — 1 decimal + {@code %}.</li>
 *   <li>{@code continuousVariable} — 1 decimal + {@code unit} (e.g. "5.6 mmol/L").</li>
 *   <li>{@code cohort} — integer (it's a patient count, decimals are nonsense).</li>
 * </ul>
 *
 * Returns {@code naLabel} (default {@code "-"}) for null / undefined / NaN.
 */
export function formatScoreValue(value: number | null | undefined, opts: FormatOptions = {}): string {
  const naLabel = opts.naLabel ?? '-'
  if (value == null || Number.isNaN(value)) return naLabel
  const family = classifyScoring(opts.scoringType)
  return formatForFamily(value, family, opts.unit)
}

/**
 * Format a threshold value (the configured target, not an actual reading).
 * Same rules as {@link formatScoreValue}; separate name documents intent at
 * call sites.
 */
export function formatThresholdValue(value: number | null | undefined, opts: FormatOptions = {}): string {
  return formatScoreValue(value, opts)
}

function formatForFamily(value: number, family: ScoringFamily, unit?: string): string {
  switch (family) {
    case 'proportion':
      return `${value.toFixed(1)}%`
    case 'continuousVariable':
      // Auto-pick decimals by magnitude (mirrors ScoreTrendChart.formatRawValue),
      // then append the unit if one was provided. Fallback to no unit rather
      // than showing nothing — the bare number is still readable.
      return unit ? `${formatRaw(value)} ${unit}` : formatRaw(value)
    case 'cohort':
      // Patient count — integer only.
      return unit ? `${Math.round(value)} ${unit}` : `${Math.round(value)}`
  }
}

function formatRaw(v: number): string {
  if (Math.abs(v) >= 100) return v.toFixed(0)
  if (Math.abs(v) >= 10) return v.toFixed(1)
  return v.toFixed(2)
}

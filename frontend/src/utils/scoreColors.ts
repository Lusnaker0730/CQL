/**
 * Shared score-to-color utilities for measure score thresholds.
 * Thresholds: >= 80 = success/green, >= 60 = warning/orange, < 60 = error/red.
 */

/** MUI Chip / LinearProgress color token */
export type ScoreChipColor = 'success' | 'warning' | 'error'

export function getScoreChipColor(score: number | null | undefined): ScoreChipColor {
  if (score == null || score < 60) return 'error'
  if (score >= 80) return 'success'
  return 'warning'
}

/** MUI theme palette path (e.g. for Typography sx={{ color }}) */
export function getScoreThemeColor(score: number | null | undefined): string {
  if (score == null) return 'text.disabled'
  if (score >= 80) return 'success.main'
  if (score >= 60) return 'warning.main'
  return 'error.main'
}

/** Hex color for inline styles and charts */
export function getScoreHex(score: number | null | undefined): string {
  if (score == null) return '#9E9E9E'
  if (score >= 80) return '#2E7D32'
  if (score >= 60) return '#ED6C02'
  return '#D32F2F'
}

/**
 * Centralized layout constants for consistent page and component sizing.
 */

/** Standard page content height — fills the flex-allocated space between Header and Footer. */
export const PAGE_CONTENT_HEIGHT = '100%'

/** Default height for Monaco / code editors (px string for Monaco, number for sx) */
export const EDITOR_HEIGHT = '500px'

/** Default height for inline/secondary editors (px string) */
export const EDITOR_HEIGHT_MEDIUM = '350px'

/** Default height for compact editors (px string) */
export const EDITOR_HEIGHT_SMALL = '300px'

/** Default height for Recharts chart containers */
export const CHART_HEIGHT = 300

/** Max height for scrollable list containers */
export const LIST_MAX_HEIGHT = 500

/** Default max-height for CQL preview boxes */
export const PREVIEW_MAX_HEIGHT = 120

/** Row height for virtualized artifact lists */
export const ARTIFACT_ROW_HEIGHT = 52

/** Height of the AppBar / top toolbar (px) */
export const APP_BAR_HEIGHT = 64

/**
 * Corner radius for card-like surfaces (Paper panels, feature cards, code frames).
 *
 * These are MUI `sx` multipliers, not pixels — `sx={{ borderRadius: CARD_RADIUS }}` resolves to
 * `CARD_RADIUS * theme.shape.borderRadius`, and `shape.borderRadius` is 10 (see `theme.ts`).
 * So CARD_RADIUS → 20px and INNER_RADIUS → 10px.
 *
 * The public pages used to mix `borderRadius: 3` (30px, older learn tabs + landing) with
 * `borderRadius: 2` (20px, the eCQM/FHIRPath/LanguageReference tabs added later), so cards
 * visibly changed shape when switching tabs. Import these instead of hard-coding a multiplier.
 */
export const CARD_RADIUS = 2

/** Corner radius for surfaces nested inside a card (inline tiles, code blocks) — stays tighter
 *  than CARD_RADIUS so the nesting reads as nesting. */
export const INNER_RADIUS = 1

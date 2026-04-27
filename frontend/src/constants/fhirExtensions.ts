/**
 * Base URL for TW Core IG address sub-field extensions.
 * Concatenated with a suffix (e.g. "section", "village", "lane") to form the full
 * StructureDefinition URL.
 *
 * Example: TW_ADDRESS_EXT_BASE + 'lane'
 *   → https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/tw-lane
 */
export const TW_ADDRESS_EXT_BASE =
  'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/tw-'

export const TW_ADDRESS_EXT_SUFFIXES = [
  'section',
  'village',
  'neighborhood',
  'lane',
  'alley',
  'number',
  'floor',
  'room',
] as const

export type TwAddressExtSuffix = (typeof TW_ADDRESS_EXT_SUFFIXES)[number]

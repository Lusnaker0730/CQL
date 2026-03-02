/**
 * Extract the human-readable name from a CQL-quoted string.
 * Input:  "Diabetes": 'http://example.org/ValueSet/diabetes'
 * Output: Diabetes
 */
export function extractCqlName(raw: string): string {
  const m = raw.match(/^"([^"]+)"/)
  return m ? m[1] : raw
}

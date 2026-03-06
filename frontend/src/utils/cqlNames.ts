/**
 * Extract the human-readable name from a CQL-quoted string.
 * Input:  "Diabetes": 'http://example.org/ValueSet/diabetes'
 * Output: Diabetes
 */
export function extractCqlName(raw: string): string {
  const m = raw.match(/^"([^"]+)"/)
  return m ? m[1] : raw
}

/** Parsed CQL define block: name → body text */
export interface CqlDefineBlock {
  name: string
  body: string
}

const DEFINE_PATTERN = /define\s+"([^"]+)":\s*\n?([\s\S]*?)(?=\ndefine\s+"|$)/g
const QUOTED_REF_PATTERN = /"([^"]+)"/g

/**
 * Parse CQL content into define blocks (name + body).
 * Reusable across dependency graph, reference counting, etc.
 */
export function parseCqlDefineBlocks(cqlContent: string): CqlDefineBlock[] {
  const blocks: CqlDefineBlock[] = []
  DEFINE_PATTERN.lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = DEFINE_PATTERN.exec(cqlContent)) !== null) {
    blocks.push({ name: match[1], body: match[2] })
  }
  return blocks
}

/**
 * Find all quoted references within a CQL body text,
 * filtering to only names in the provided known set.
 */
export function findQuotedReferences(body: string, knownNames: Set<string>, excludeName?: string): string[] {
  const refs: string[] = []
  QUOTED_REF_PATTERN.lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = QUOTED_REF_PATTERN.exec(body)) !== null) {
    const name = match[1]
    if (name !== excludeName && knownNames.has(name) && !refs.includes(name)) {
      refs.push(name)
    }
  }
  return refs
}

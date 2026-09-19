import type { PlatformValueSet, ValueSetConcept } from '../types'

export interface ParsedCodes {
  concepts: ValueSetConcept[]
  /** 1-based line numbers that could not be read (no code, or no system and no default system). */
  skippedLines: number[]
}

/**
 * PAT-230 — codes pasted from a spreadsheet or a CSV, one per line:
 *
 *   system , code , display        three columns
 *   code , display                 two columns  → `defaultSystem`
 *   code                           one column   → `defaultSystem`
 *
 * Tab, comma, semicolon and the full-width comma all separate columns (a display may contain
 * commas when the separator is a tab). A first column that looks like a URL / URN / OID is a
 * system even in a two-column line. Header lines ("system,code,display") are ignored.
 */
export function parsePastedCodes(text: string, defaultSystem: string): ParsedCodes {
  const concepts: ValueSetConcept[] = []
  const skippedLines: number[] = []
  const fallback = defaultSystem.trim()

  text.split(/\r?\n/).forEach((raw, index) => {
    const line = raw.trim()
    if (!line) return
    const separator = line.includes('\t') ? /\t/ : /[,;，]/
    const cells = line.split(separator).map((c) => c.trim().replace(/^"(.*)"$/, '$1'))
    if (cells[0]?.toLowerCase() === 'system' || cells[0]?.toLowerCase() === 'code') return

    let system = fallback
    let code = ''
    let display: string | undefined
    const looksLikeSystem = /^(https?:\/\/|urn:)/i.test(cells[0] ?? '')
    if (cells.length >= 3 || (cells.length === 2 && looksLikeSystem)) {
      system = cells[0] || fallback
      code = cells[1] ?? ''
      display = cells.slice(2).join(', ') || undefined
    } else {
      code = cells[0] ?? ''
      display = cells[1] || undefined
    }
    if (!code || !system) {
      skippedLines.push(index + 1)
      return
    }
    concepts.push(display ? { system, code, display } : { system, code })
  })
  return { concepts, skippedLines }
}

/** Append `added` to `existing`, dropping codes already there (same system + version + code). */
export function mergeConcepts(existing: ValueSetConcept[], added: ValueSetConcept[]): ValueSetConcept[] {
  const key = (c: ValueSetConcept) => `${c.system}|${c.version ?? ''}|${c.code}`
  const seen = new Set(existing.map(key))
  const result = [...existing]
  for (const c of added) {
    if (!seen.has(key(c))) {
      seen.add(key(c))
      result.push(c)
    }
  }
  return result
}

/** The CQL header line for a value set; `pin` adds the version so the codes can never shift. */
export function cqlDeclaration(vs: Pick<PlatformValueSet, 'name' | 'title' | 'url' | 'version'>, pin: boolean): string {
  const identifier = (vs.title || vs.name).replace(/\\/g, '\\\\').replace(/"/g, '\\"')
  const url = vs.url.replace(/\\/g, '\\\\').replace(/'/g, "\\'")
  return `valueset "${identifier}": '${url}'${pin ? ` version '${vs.version.replace(/'/g, "\\'")}'` : ''}`
}

/** Next patch version for the "new version" prompt: 1.2.3 → 1.2.4; anything else gets "-2". */
export function suggestNextVersion(version: string): string {
  const m = /^(\d+)\.(\d+)\.(\d+)$/.exec(version.trim())
  return m ? `${m[1]}.${m[2]}.${Number(m[3]) + 1}` : `${version.trim()}-2`
}

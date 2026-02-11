/**
 * Finds the line range (1-based) of a CQL element in the editor content.
 */

export type CqlElementType = 'include' | 'valueset' | 'codesystem' | 'code' | 'concept' | 'parameter' | 'define' | 'function'

export interface LineRange {
  startLine: number
  endLine: number
}

// Top-level declaration keywords that start a new block
const TOP_LEVEL_RE = /^(library|using|include|codesystem|valueset|code|concept|parameter|context|define)\b/

/**
 * Find the line range for a CQL element by type and identifier.
 * Identifier is the quoted name (e.g. "Has Diabetes") for definitions,
 * or the raw text for includes (e.g. "FHIRHelpers").
 */
export function findElementLineRange(
  cqlContent: string,
  elementType: CqlElementType,
  identifier: string
): LineRange | null {
  const lines = cqlContent.split('\n')

  switch (elementType) {
    case 'include':
      return findSingleLine(lines, new RegExp(`^include\\s+.*\\b${escapeRegex(identifier)}\\b`))

    case 'valueset':
      return findSingleLine(lines, new RegExp(`^valueset\\s+"${escapeRegex(identifier)}"`))

    case 'codesystem':
      return findSingleLine(lines, new RegExp(`^codesystem\\s+"${escapeRegex(identifier)}"`))

    case 'code':
      return findSingleLine(lines, new RegExp(`^code\\s+"${escapeRegex(identifier)}"`))

    case 'concept':
      return findSingleLine(lines, new RegExp(`^concept\\s+"${escapeRegex(identifier)}"`))

    case 'parameter':
      return findSingleLine(lines, new RegExp(`^parameter\\s+"${escapeRegex(identifier)}"`))

    case 'define':
      return findMultiLine(lines, new RegExp(`^define\\s+"${escapeRegex(identifier)}"\\s*:`))

    case 'function':
      return findMultiLine(lines, new RegExp(`^define\\s+function\\s+"${escapeRegex(identifier)}"\\s*\\(`))

    default:
      return null
  }
}

function findSingleLine(lines: string[], pattern: RegExp): LineRange | null {
  for (let i = 0; i < lines.length; i++) {
    if (pattern.test(lines[i].trim())) {
      // Some single-line declarations can span to the next line (parameter with default)
      let endLine = i
      // Check if next lines are continuations (indented, no top-level keyword)
      while (endLine + 1 < lines.length) {
        const nextTrimmed = lines[endLine + 1].trim()
        if (nextTrimmed === '' || TOP_LEVEL_RE.test(nextTrimmed)) break
        endLine++
      }
      return { startLine: i + 1, endLine: endLine + 1 }
    }
  }
  return null
}

function findMultiLine(lines: string[], pattern: RegExp): LineRange | null {
  for (let i = 0; i < lines.length; i++) {
    if (pattern.test(lines[i].trim())) {
      // Scan forward until we hit the next top-level declaration or EOF
      let endLine = i
      for (let j = i + 1; j < lines.length; j++) {
        const trimmed = lines[j].trim()
        if (trimmed === '') {
          // Could be a blank line separator — peek ahead
          const ahead = j + 1 < lines.length ? lines[j + 1].trim() : ''
          if (TOP_LEVEL_RE.test(ahead) || ahead === '') {
            break
          }
          endLine = j
        } else if (TOP_LEVEL_RE.test(trimmed)) {
          break
        } else {
          endLine = j
        }
      }
      return { startLine: i + 1, endLine: endLine + 1 }
    }
  }
  return null
}

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

import type { StratifierComponentElement } from '../types/ecqm'

/** Same rule as the backend's component-code check: the code becomes part of a CQL identifier. */
export const COMPONENT_CODE_RE = /^[A-Za-z0-9][A-Za-z0-9 _.-]{0,49}$/
export const MAX_STRATIFIER_COMPONENTS = 10

/** A problem with a stratifier's component list, as an i18n key under `ecqm:stratifiers.componentErrors`. */
export type ComponentProblem = 'tooFew' | 'tooMany' | 'code' | 'duplicate'

/**
 * PAT-235 — what the backend will refuse before it turns components into defines, so the author
 * hears about it while editing. Returns the distinct problems in the order found; empty = valid.
 */
export function validateStratifierComponents(components: StratifierComponentElement[] | undefined): ComponentProblem[] {
  const problems: ComponentProblem[] = []
  const add = (p: ComponentProblem) => { if (!problems.includes(p)) problems.push(p) }
  if (!components || components.length < 2) return ['tooFew']
  if (components.length > MAX_STRATIFIER_COMPONENTS) add('tooMany')
  const codes = new Set<string>()
  for (const component of components) {
    const code = (component.code ?? '').trim()
    if (!COMPONENT_CODE_RE.test(code)) add('code')
    if (codes.has(code)) add('duplicate')
    codes.add(code)
  }
  return problems
}

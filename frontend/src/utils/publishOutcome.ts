import type { PublishResult } from '../types/ecqm'

/**
 * BUG-147 — which message a publish result deserves (i18n key + params, `ecqm` namespace).
 * A publish never approves: a first publish is a draft that still needs review; changed logic of
 * an approved measure becomes a new draft version that replaces the approved one once approved;
 * a publish that only changed metadata of an approved measure keeps it approved.
 */
export function publishOutcomeMessage(result: PublishResult | undefined): { key: string; params?: Record<string, unknown> } {
  if (result?.newVersion) {
    return { key: 'publishOutcome.newVersion', params: { version: result.measureVersion } }
  }
  if (result?.measureStatus === 'draft') {
    return { key: 'publishOutcome.draft', params: { id: result.measureDefinitionId } }
  }
  return { key: 'publishOutcome.updated', params: { id: result?.measureDefinitionId } }
}

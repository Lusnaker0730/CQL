import { describe, it, expect } from 'vitest'
import { publishOutcomeMessage } from '../publishOutcome'
import type { PublishResult } from '../../types/ecqm'

// BUG-147 — a publish never approves; the message says what the author has to do next.
describe('publishOutcomeMessage', () => {
  const base: PublishResult = { measureDefinitionId: 5, measureName: 'M', cql: '', message: '' }

  it('first publish → draft that needs review', () => {
    expect(publishOutcomeMessage({ ...base, measureStatus: 'draft' })).toEqual({ key: 'publishOutcome.draft', params: { id: 5 } })
  })

  it('changed logic of an approved measure → new draft version', () => {
    expect(publishOutcomeMessage({ ...base, measureStatus: 'draft', newVersion: true, measureVersion: '1.1.0' }))
      .toEqual({ key: 'publishOutcome.newVersion', params: { version: '1.1.0' } })
  })

  it('metadata-only publish on an approved measure → updated in place', () => {
    expect(publishOutcomeMessage({ ...base, measureStatus: 'active' })).toEqual({ key: 'publishOutcome.updated', params: { id: 5 } })
  })
})

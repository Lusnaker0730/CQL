import { describe, it, expect } from 'vitest'
import { validateStratifierComponents } from '../stratifierComponents'

// PAT-235 — mirrors the backend's component-code checks.
describe('validateStratifierComponents', () => {
  it('accepts two or more plainly coded components', () => {
    expect(validateStratifierComponents([{ code: 'sex' }, { code: 'age band' }, { code: 'dx.1-2_x' }])).toEqual([])
  })

  it('reports each distinct problem once', () => {
    expect(validateStratifierComponents(undefined)).toEqual(['tooFew'])
    expect(validateStratifierComponents([{ code: 'sex' }])).toEqual(['tooFew'])
    expect(validateStratifierComponents([{ code: 'a<b' }, { code: '' }])).toEqual(['code'])
    expect(validateStratifierComponents([{ code: 'sex' }, { code: 'sex' }])).toEqual(['duplicate'])
    expect(validateStratifierComponents([{ code: '性別' }, { code: 'age' }])).toEqual(['code'])
    expect(validateStratifierComponents(Array.from({ length: 11 }, (_, i) => ({ code: `c${i}` })))).toEqual(['tooMany'])
  })
})

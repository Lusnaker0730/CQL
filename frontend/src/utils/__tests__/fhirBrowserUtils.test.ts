import { describe, it, expect } from 'vitest'
import {
  formatJson,
  getResourceCount,
  getDisplayFields,
  extractPaginationLinks,
  extractSearchParamsFromUrl,
  RESOURCE_DISPLAY_FIELDS,
  FHIR_RESOURCE_TYPES,
  type FhirResource,
} from '../fhirBrowserUtils'

describe('formatJson', () => {
  it('should format an object', () => {
    const result = formatJson({ a: 1 })
    expect(result).toBe('{\n  "a": 1\n}')
  })

  it('should format a JSON string', () => {
    const result = formatJson('{"a":1}')
    expect(result).toBe('{\n  "a": 1\n}')
  })

  it('should return string representation for non-JSON', () => {
    const result = formatJson('not json')
    expect(result).toBe('not json')
  })
})

describe('getResourceCount', () => {
  it('should return 0 for null/undefined', () => {
    expect(getResourceCount(null)).toBe(0)
    expect(getResourceCount(undefined)).toBe(0)
  })

  it('should return total when present', () => {
    expect(getResourceCount({ total: 42 })).toBe(42)
  })

  it('should count entries when no total', () => {
    expect(getResourceCount({ entry: [1, 2, 3] })).toBe(3)
  })

  it('should parse JSON string input', () => {
    expect(getResourceCount('{"total": 5}')).toBe(5)
  })

  it('should return 0 for invalid JSON string', () => {
    expect(getResourceCount('{bad}')).toBe(0)
  })
})

describe('getDisplayFields', () => {
  it('should return Patient fields', () => {
    const fields = getDisplayFields('Patient')
    expect(fields.length).toBeGreaterThan(0)
    expect(fields.map(f => f.key)).toContain('name')
  })

  it('should return fallback fields for unknown type', () => {
    const fields = getDisplayFields('UnknownResource')
    expect(fields.length).toBeGreaterThan(0)
    expect(fields[0].key).toBe('lastUpdated')
  })

  it('should extract Patient name correctly', () => {
    const fields = getDisplayFields('Patient')
    const nameField = fields.find(f => f.key === 'name')!
    const patient: FhirResource = {
      resourceType: 'Patient',
      name: [{ family: 'Smith', given: ['John'] }],
    }
    expect(nameField.extract(patient)).toBe('Smith, John')
  })

  it('should extract Observation code display', () => {
    const fields = getDisplayFields('Observation')
    const codeField = fields.find(f => f.key === 'code')!
    const obs: FhirResource = {
      resourceType: 'Observation',
      code: { coding: [{ display: 'Blood Pressure' }] },
    }
    expect(codeField.extract(obs)).toBe('Blood Pressure')
  })
})

describe('extractPaginationLinks', () => {
  it('should return empty object when no links', () => {
    expect(extractPaginationLinks({})).toEqual({})
  })

  it('should extract next link', () => {
    const bundle = {
      link: [{ relation: 'next', url: 'http://example.com/next' }],
    }
    expect(extractPaginationLinks(bundle).next).toBe('http://example.com/next')
  })

  it('should extract prev link with "previous" relation', () => {
    const bundle = {
      link: [{ relation: 'previous', url: 'http://example.com/prev' }],
    }
    expect(extractPaginationLinks(bundle).prev).toBe('http://example.com/prev')
  })

  it('should extract prev link with "prev" relation', () => {
    const bundle = {
      link: [{ relation: 'prev', url: 'http://example.com/prev' }],
    }
    expect(extractPaginationLinks(bundle).prev).toBe('http://example.com/prev')
  })
})

describe('extractSearchParamsFromUrl', () => {
  it('should extract resourceType from URL path', () => {
    const result = extractSearchParamsFromUrl('http://example.com/fhir/Patient?name=Smith')
    expect(result.resourceType).toBe('Patient')
    expect(result.params).toBe('name=Smith')
  })

  it('should return Patient as default for invalid URL', () => {
    const result = extractSearchParamsFromUrl('not-a-url')
    expect(result.resourceType).toBe('Patient')
    expect(result.params).toBe('')
  })

  it('should handle URL without query params', () => {
    const result = extractSearchParamsFromUrl('http://example.com/fhir/Observation')
    expect(result.resourceType).toBe('Observation')
    expect(result.params).toBe('')
  })
})

describe('FHIR_RESOURCE_TYPES', () => {
  it('should be derived from RESOURCE_DISPLAY_FIELDS', () => {
    expect(FHIR_RESOURCE_TYPES).toEqual(Object.keys(RESOURCE_DISPLAY_FIELDS))
  })

  it('should include common types', () => {
    expect(FHIR_RESOURCE_TYPES).toContain('Patient')
    expect(FHIR_RESOURCE_TYPES).toContain('Observation')
    expect(FHIR_RESOURCE_TYPES).toContain('Condition')
  })
})

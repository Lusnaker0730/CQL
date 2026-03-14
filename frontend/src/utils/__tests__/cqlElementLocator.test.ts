import { describe, it, expect } from 'vitest'
import { findElementLineRange } from '../cqlElementLocator'

const SAMPLE_CQL = `library TestLibrary version '1.0'
using FHIR version '4.0.1'

include FHIRHelpers version '4.0.1'

valueset "Diabetes Codes": 'http://example.org/vs/diabetes'

parameter "Measurement Period" Interval<DateTime>
  default Interval[@2024-01-01, @2024-12-31]

context Patient

define "Has Diabetes":
  exists([Condition: "Diabetes Codes"])

define function "Age In Years"(birthDate DateTime):
  CalculateAgeInYearsAt(birthDate, Today())

define "Is Adult":
  AgeInYears() >= 18`

describe('findElementLineRange', () => {
  it('should find include line', () => {
    const result = findElementLineRange(SAMPLE_CQL, 'include', 'FHIRHelpers')
    expect(result).not.toBeNull()
    expect(result!.startLine).toBe(4)
  })

  it('should find valueset line', () => {
    const result = findElementLineRange(SAMPLE_CQL, 'valueset', 'Diabetes Codes')
    expect(result).not.toBeNull()
    expect(result!.startLine).toBe(6)
  })

  it('should find parameter with multi-line default', () => {
    const result = findElementLineRange(SAMPLE_CQL, 'parameter', 'Measurement Period')
    expect(result).not.toBeNull()
    expect(result!.startLine).toBe(8)
    expect(result!.endLine).toBeGreaterThanOrEqual(9)
  })

  it('should find define with body', () => {
    const result = findElementLineRange(SAMPLE_CQL, 'define', 'Has Diabetes')
    expect(result).not.toBeNull()
    expect(result!.startLine).toBe(13)
    expect(result!.endLine).toBe(14)
  })

  it('should find function', () => {
    const result = findElementLineRange(SAMPLE_CQL, 'function', 'Age In Years')
    expect(result).not.toBeNull()
    expect(result!.startLine).toBe(16)
    expect(result!.endLine).toBe(17)
  })

  it('should return null for non-existent element', () => {
    const result = findElementLineRange(SAMPLE_CQL, 'define', 'Does Not Exist')
    expect(result).toBeNull()
  })

  it('should return null for non-existent include', () => {
    const result = findElementLineRange(SAMPLE_CQL, 'include', 'NonExistent')
    expect(result).toBeNull()
  })

  it('should handle empty content', () => {
    const result = findElementLineRange('', 'define', 'Foo')
    expect(result).toBeNull()
  })
})

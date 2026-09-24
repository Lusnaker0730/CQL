import { describe, it, expect } from 'vitest'
import {
  defaultArgument,
  functionArgumentsOf,
  functionCallElement,
  functionSignature,
  literalTypeFor,
  returnTypeForCql,
  validateFunctionArguments,
} from '../libraryFunctions'
import type { FunctionArgument } from '../../types/authoring'

// PAT-237 — the rules here mirror ExpressionCqlEngine.resolveFunctionArgument / functionLiteral:
// anything this accepts the backend emits, anything it flags the backend leaves unresolved.
describe('libraryFunctions', () => {
  it('formats a signature the way the picker and palette show it', () => {
    expect(functionSignature('AtLeast', [{ name: 'value', type: 'Integer' }, { name: 'threshold', type: 'Integer' }], 'Boolean'))
      .toBe('AtLeast(value Integer, threshold Integer) → Boolean')
    expect(functionSignature('Any', [], undefined)).toBe('Any()')
    expect(functionSignature('Any', undefined, 'Boolean')).toBe('Any() → Boolean')
  })

  it('maps declared operand types to literal types and starting modes', () => {
    expect(literalTypeFor('Integer')).toBe('Integer')
    expect(literalTypeFor('System.Decimal')).toBe('Decimal')
    expect(literalTypeFor('List<FHIR.Observation>')).toBeNull()

    expect(defaultArgument({ name: 'n', type: 'Integer' }, false)).toEqual({ name: 'n', type: 'Integer', mode: 'literal', literal_type: 'Integer', literal_value: '' })
    expect(defaultArgument({ name: 'b', type: 'Boolean' }, false).literal_value).toBe('true')
    expect(defaultArgument({ name: 'p', type: 'Interval<DateTime>' }, true).mode).toBe('measurementPeriod')
    expect(defaultArgument({ name: 'p', type: 'Interval<DateTime>' }, false).mode).toBe('element')
    expect(defaultArgument({ name: 'pt', type: 'FHIR.Patient' }, false).mode).toBe('patient')
    expect(defaultArgument({ name: 'obs', type: 'List<FHIR.Observation>' }, false).mode).toBe('element')
  })

  it('maps CQL result types to builder return types', () => {
    expect(returnTypeForCql('Boolean')).toBe('boolean')
    expect(returnTypeForCql('System.Quantity')).toBe('system_quantity')
    expect(returnTypeForCql('List<FHIR.Observation>')).toBe('list_of_observations')
    expect(returnTypeForCql('Observation')).toBe('observation')
    expect(returnTypeForCql('FHIR.Encounter')).toBe('encounter')
    expect(returnTypeForCql(undefined)).toBe('unknown')
    expect(returnTypeForCql('Tuple{a Integer}')).toBe('Tuple{a Integer}')
  })

  it('builds a call element with the static source fields and an editable arguments field', () => {
    const element = functionCallElement({
      libraryName: 'HospitalCommon', libraryVersion: '2.0.0', functionName: 'Most Recent Below',
      operands: [{ name: 'observations', type: 'List<FHIR.Observation>' }, { name: 'threshold', type: 'Decimal' }],
      resultType: 'Boolean', referenceId: '7:Most Recent Below',
    }, false)

    expect(element.type).toBe('externalCqlFunctionCall')
    expect(element.name).toBe('Most Recent Below')
    expect(element.returnType).toBe('boolean')
    expect(element.fields.map((f) => `${f.id}${f.static ? '*' : ''}`))
      .toEqual(['element_name', 'library_name*', 'library_version*', 'function_name*', 'reference_id*', 'arguments'])
    expect(functionArgumentsOf(element)).toHaveLength(2)
    expect(functionArgumentsOf({ ...element, fields: [] })).toEqual([])
  })

  it('validates arguments with the backend rules', () => {
    const ok: FunctionArgument[] = [
      { name: 'a', mode: 'element', operand_id: 'be-1' },
      { name: 'b', mode: 'parameter', operand_id: 'p-1' },
      { name: 'c', mode: 'patient' },
      { name: 'd', mode: 'measurementPeriod' },
      { name: 'e', mode: 'literal', literal_type: 'Integer', literal_value: '65' },
      { name: 'f', mode: 'literal', literal_type: 'Decimal', literal_value: '-7.25' },
      { name: 'g', mode: 'literal', literal_type: 'String', literal_value: "it's < 7% & fine" },
      { name: 'h', mode: 'literal', literal_type: 'Boolean', literal_value: 'false' },
      { name: 'i', mode: 'literal', literal_type: 'Date', literal_value: '2026-01-01' },
      { name: 'j', mode: 'literal', literal_type: 'DateTime', literal_value: '2026-01-01T08:30' },
      { name: 'k', mode: 'literal', literal_type: 'Quantity', literal_value: '140', literal_unit: 'mm[Hg]' },
    ]
    expect(validateFunctionArguments(ok, true)).toEqual(ok.map(() => null))

    const bad: FunctionArgument[] = [
      { name: 'a', mode: 'element' },
      { name: 'b', mode: 'parameter', operand_id: '' },
      { name: 'd', mode: 'measurementPeriod' },
      { name: 'e', mode: 'literal', literal_type: 'Integer', literal_value: '3; drop' },
      { name: 'f', mode: 'literal', literal_type: 'Decimal', literal_value: '1e5' },
      { name: 'h', mode: 'literal', literal_type: 'Boolean', literal_value: 'yes' },
      { name: 'i', mode: 'literal', literal_type: 'Date', literal_value: '2026/01/01' },
      { name: 'k', mode: 'literal', literal_type: 'Quantity', literal_value: '5', literal_unit: "mg' or 1=1" },
      { name: 'l', mode: 'literal' },
      { name: 'm', mode: 'nope' as FunctionArgument['mode'] },
    ]
    expect(validateFunctionArguments(bad, false)).toEqual([
      'missingReference', 'missingReference', 'noMeasurementPeriod', 'invalidLiteral', 'invalidLiteral',
      'invalidLiteral', 'invalidLiteral', 'invalidUnit', 'invalidLiteral', 'unknownMode',
    ])
  })
})

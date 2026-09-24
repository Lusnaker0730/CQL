import type {
  ElementInstance,
  FunctionArgument,
  FunctionArgumentMode,
  LiteralType,
} from '../types/authoring'
import { generateId } from './validation'

/**
 * PAT-237 — library function calls in the builder.
 *
 * An `externalCqlFunctionCall` element calls a `define function` of an included CQL library with
 * author-supplied arguments; the backend (`ExpressionCqlEngine.emitFunctionCall`) emits
 * `"Lib"."Fn"(arg, …)` and refuses any argument outside the shapes validated here, so the rules
 * below mirror its allow-lists exactly (an argument the UI accepts must never be dropped by the
 * server).
 */

export const FUNCTION_CALL_TYPE = 'externalCqlFunctionCall'

export const LITERAL_TYPES: readonly LiteralType[] = ['Integer', 'Decimal', 'String', 'Boolean', 'Date', 'DateTime', 'Quantity']

export const ARGUMENT_MODES: readonly FunctionArgumentMode[] = ['element', 'parameter', 'literal', 'patient', 'measurementPeriod']

/** Same shapes as the backend's FN_* patterns. */
const LITERAL_PATTERNS: Record<LiteralType, RegExp> = {
  Integer: /^-?\d{1,18}$/,
  Decimal: /^-?\d{1,18}(\.\d{1,8})?$/,
  String: /^[\s\S]{0,500}$/,
  Boolean: /^(true|false)$/,
  Date: /^\d{4}-\d{2}-\d{2}$/,
  DateTime: /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?(Z|[+-]\d{2}:\d{2})?$/,
  Quantity: /^-?\d+(\.\d+)?$/,
}
const UCUM_UNIT_PATTERN = /^[A-Za-z0-9./*+\-()[\]{}%_]{1,32}$/

export interface LibraryFunctionSource {
  libraryName: string
  libraryVersion?: string
  /** The `called` alias when the author picked one (shared-library picker); else the library name is used. */
  alias?: string
  functionName: string
  operands: Array<{ name: string; type: string }>
  resultType?: string
  /** Per-artifact uploaded library reference (`<libId>:<fn>`), when it comes from the upload tab. */
  referenceId?: string
}

/** `Fn(a Integer, b String) → Boolean` for lists and pickers. */
export function functionSignature(name: string, operands: Array<{ name: string; type: string }> | undefined, resultType?: string): string {
  const params = (operands ?? []).map((o) => `${o.name} ${o.type}`).join(', ')
  return `${name}(${params})${resultType ? ` → ${resultType}` : ''}`
}

/** The literal type a declared operand type maps to, or null when it is not a scalar we can type in. */
export function literalTypeFor(declaredType: string | undefined): LiteralType | null {
  const t = (declaredType ?? '').replace(/^System\./, '')
  return (LITERAL_TYPES as readonly string[]).includes(t) ? (t as LiteralType) : null
}

/**
 * The starting argument for a declared operand: a scalar type starts as a typed literal, an
 * `Interval<DateTime>` as the Measurement Period (eCQM) and `Patient` as the patient; anything
 * else (lists, resources, codes) starts as a base-element reference the author still has to pick.
 */
export function defaultArgument(operand: { name: string; type: string }, hasMeasurementPeriod: boolean): FunctionArgument {
  const type = operand.type ?? ''
  const literal = literalTypeFor(type)
  if (literal) return { name: operand.name, type, mode: 'literal', literal_type: literal, literal_value: literal === 'Boolean' ? 'true' : '' }
  if (/^Interval<(System\.)?DateTime>$/.test(type) && hasMeasurementPeriod) return { name: operand.name, type, mode: 'measurementPeriod' }
  if (/^(FHIR\.)?Patient$/.test(type)) return { name: operand.name, type, mode: 'patient' }
  return { name: operand.name, type, mode: 'element' }
}

/** The builder return type for a CQL result type, in the same terms the templates use. */
export function returnTypeForCql(resultType: string | undefined): string {
  if (!resultType) return 'unknown'
  const t = resultType.replace(/^System\./, '')
  const scalar: Record<string, string> = {
    Boolean: 'boolean', Integer: 'integer', Decimal: 'decimal', String: 'string',
    DateTime: 'system_date_time', Date: 'system_date', Time: 'system_time',
    Quantity: 'system_quantity', Code: 'system_code', Concept: 'system_concept',
  }
  if (scalar[t]) return scalar[t]
  const list = /^List<(FHIR\.)?([A-Za-z]+)>$/.exec(t)
  if (list) return `list_of_${list[2].toLowerCase()}s`
  const single = /^(FHIR\.)?([A-Z][A-Za-z]+)$/.exec(t)
  if (single) return single[2].toLowerCase()
  return t
}

/** The element the builder stores for a call: static source fields + the editable `arguments` field. */
export function functionCallElement(source: LibraryFunctionSource, hasMeasurementPeriod: boolean): ElementInstance {
  const args = source.operands.map((o) => defaultArgument(o, hasMeasurementPeriod))
  const fields: ElementInstance['fields'] = [
    { id: 'element_name', type: 'string', name: 'Element Name', value: source.functionName },
    { id: 'library_name', type: 'string', name: 'Library', value: source.libraryName, static: true },
    ...(source.libraryVersion ? [{ id: 'library_version', type: 'string', name: 'Library Version', value: source.libraryVersion, static: true }] : []),
    ...(source.alias ? [{ id: 'alias', type: 'string', name: 'Alias', value: source.alias, static: true }] : []),
    { id: 'function_name', type: 'string', name: 'Function', value: source.functionName, static: true },
    ...(source.referenceId ? [{ id: 'reference_id', type: 'string', name: 'Reference ID', value: source.referenceId, static: true }] : []),
    { id: 'arguments', type: 'functionArguments', name: 'Arguments', value: args },
  ]
  return {
    uniqueId: generateId(),
    type: FUNCTION_CALL_TYPE,
    name: source.functionName,
    returnType: returnTypeForCql(source.resultType),
    fields,
    modifiers: [],
  }
}

export type ArgumentError = 'missingReference' | 'invalidLiteral' | 'invalidUnit' | 'noMeasurementPeriod' | 'unknownMode'

/**
 * Per-argument problems, in order (null = fine). The same conditions make the backend leave the
 * argument unresolved and emit the whole call as null, so the UI shows them before save.
 */
export function validateFunctionArguments(args: FunctionArgument[], hasMeasurementPeriod: boolean): Array<ArgumentError | null> {
  return args.map((arg) => {
    switch (arg.mode) {
      case 'element':
      case 'parameter':
        return arg.operand_id ? null : 'missingReference'
      case 'patient':
        return null
      case 'measurementPeriod':
        return hasMeasurementPeriod ? null : 'noMeasurementPeriod'
      case 'literal': {
        const type = arg.literal_type
        if (!type || !LITERAL_PATTERNS[type]) return 'invalidLiteral'
        const value = arg.literal_value ?? ''
        if (!LITERAL_PATTERNS[type].test(type === 'String' ? value : value.trim())) return 'invalidLiteral'
        if (type === 'Quantity' && !UCUM_UNIT_PATTERN.test((arg.literal_unit ?? '').trim())) return 'invalidUnit'
        return null
      }
      default:
        return 'unknownMode'
    }
  })
}

/** The `arguments` of a stored call element (empty when the field is missing or malformed). */
export function functionArgumentsOf(element: ElementInstance): FunctionArgument[] {
  const field = element.fields?.find((f) => f.id === 'arguments')
  return Array.isArray(field?.value) ? (field!.value as FunctionArgument[]) : []
}

import type { ElementInstance } from '../types/authoring'
import type { LibraryDefinitionReference } from '../components/cql-libraries/LibraryDefinitionPicker'
import { functionCallElement } from './libraryFunctions'

/**
 * Convert a {@link LibraryDefinitionReference} chosen in the library picker
 * into an {@link ElementInstance} that the CQL artifact builder recognizes as
 * an `externalCqlElement` tree node. The backend's `ExpressionCqlEngine`
 * reads the four `fields` values (`libraryName` / `libraryVersion` / `alias`
 * / `definitionName`) to emit both the top-level `include X version 'Y'
 * called alias` statement and the `"alias"."DefName"` body reference.
 *
 * <p>Extracted so eCQM and CDS authoring produce identical element shapes —
 * previously the logic lived inline in {@code EcqmPopulationTreeEditor}
 * with no shared contract. A drift would silently produce different CQL
 * outputs between the two authoring paths for the same library pick.
 */
export function libraryReferenceToElement(reference: LibraryDefinitionReference, hasMeasurementPeriod = false): ElementInstance {
  if (reference.kind === 'function') {
    // PAT-237: a function becomes a call element with one argument slot per operand; the alias
    // is what the include is `called` and what the call is qualified with.
    return functionCallElement({
      libraryName: reference.libraryName,
      libraryVersion: reference.libraryVersion,
      alias: reference.alias,
      functionName: reference.definitionName,
      operands: reference.operands ?? [],
      resultType: reference.resultType,
    }, hasMeasurementPeriod)
  }
  return {
    uniqueId: `libref-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`,
    type: 'externalCqlElement',
    name: `${reference.alias}."${reference.definitionName}"`,
    returnType: 'boolean',
    fields: [
      { id: 'libraryName', type: 'string', name: 'Library Name', value: reference.libraryName, static: true },
      { id: 'libraryVersion', type: 'string', name: 'Library Version', value: reference.libraryVersion, static: true },
      { id: 'definitionName', type: 'string', name: 'Definition Name', value: reference.definitionName, static: true },
      { id: 'alias', type: 'string', name: 'Alias', value: reference.alias, static: true },
    ],
    modifiers: [],
  }
}

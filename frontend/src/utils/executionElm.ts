/**
 * Decide whether a pre-compiled ELM may be sent with a CQL execution request.
 *
 * The backend skips the CPU-heavy cql2elm translation when `elmJson` is present
 * — a large saving on the 2-core VM. But executing an ELM that does NOT match the
 * CQL would run stale logic and produce WRONG clinical results. We therefore only
 * forward the ELM when it was translated from this exact text
 * (`elmSourceCql === cql`). Any edit since the last translate makes the strings
 * differ, so the backend re-translates the current text. (The backend also falls
 * back to translating if the ELM fails to deserialize — defence in depth.)
 *
 * @returns the ELM JSON to send, or `undefined` to make the backend translate `cql`.
 */
export function freshPrecompiledElm(
  cql: string,
  elmJson: string | null | undefined,
  elmSourceCql: string | null | undefined,
): string | undefined {
  return elmJson && elmSourceCql === cql ? elmJson : undefined
}

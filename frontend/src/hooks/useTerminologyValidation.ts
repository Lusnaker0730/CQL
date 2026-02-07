import { useState, useEffect, useRef } from 'react'
import { fhirApi } from '../api'
import type { TerminologyValidationItem } from '../types'

interface ElmValueSetDef {
  name: string
  id: string
}

interface ElmCodeDef {
  name: string
  id: string
  codeSystem: { name: string }
}

interface ElmCodeSystemDef {
  name: string
  id: string
}

export function useTerminologyValidation(elmJson: string | null) {
  const [results, setResults] = useState<TerminologyValidationItem[]>([])
  const [isValidating, setIsValidating] = useState(false)
  const lastElmRef = useRef<string | null>(null)

  useEffect(() => {
    if (!elmJson || elmJson === lastElmRef.current) return
    lastElmRef.current = elmJson

    let cancelled = false

    async function validate() {
      setIsValidating(true)
      const items: TerminologyValidationItem[] = []

      try {
        const elm = JSON.parse(elmJson!)
        const library = elm.library || {}

        const valueSetDefs: ElmValueSetDef[] = library.valueSets?.def || []
        const codeDefs: ElmCodeDef[] = library.codes?.def || []
        const codeSystemDefs: ElmCodeSystemDef[] = library.codeSystems?.def || []

        const promises: Promise<void>[] = []

        // Validate each ValueSet by attempting to expand it
        for (const vs of valueSetDefs) {
          promises.push(
            fhirApi.expandValueSet(vs.id)
              .then((expansion) => {
                if (cancelled) return
                items.push({
                  type: 'valueset',
                  name: vs.name,
                  url: vs.id,
                  status: 'valid',
                  detail: `${expansion.expansion?.total ?? expansion.expansion?.contains?.length ?? 0} codes`,
                })
              })
              .catch((err) => {
                if (cancelled) return
                items.push({
                  type: 'valueset',
                  name: vs.name,
                  url: vs.id,
                  status: 'error',
                  detail: err?.response?.data?.message || err.message || 'Failed to expand',
                })
              })
          )
        }

        // Build a map of code system names to URLs
        const codeSystemMap = new Map<string, string>()
        for (const cs of codeSystemDefs) {
          codeSystemMap.set(cs.name, cs.id)
        }

        // Validate each Code by looking it up
        for (const codeDef of codeDefs) {
          const systemUrl = codeSystemMap.get(codeDef.codeSystem.name) || ''
          if (!systemUrl) {
            items.push({
              type: 'code',
              name: codeDef.name,
              system: '',
              code: codeDef.id,
              status: 'error',
              detail: `Code system "${codeDef.codeSystem.name}" not found`,
            })
            continue
          }

          promises.push(
            fhirApi.lookupCode(systemUrl, codeDef.id)
              .then((result) => {
                if (cancelled) return
                items.push({
                  type: 'code',
                  name: codeDef.name,
                  system: systemUrl,
                  code: codeDef.id,
                  status: 'valid',
                  detail: result.display || result.name || 'Found',
                })
              })
              .catch((err) => {
                if (cancelled) return
                items.push({
                  type: 'code',
                  name: codeDef.name,
                  system: systemUrl,
                  code: codeDef.id,
                  status: 'error',
                  detail: err?.response?.data?.message || err.message || 'Lookup failed',
                })
              })
          )
        }

        await Promise.allSettled(promises)
      } catch {
        // ELM parse error — no validation possible
      }

      if (!cancelled) {
        setResults(items)
        setIsValidating(false)
      }
    }

    validate()

    return () => {
      cancelled = true
    }
  }, [elmJson])

  return { results, isValidating }
}

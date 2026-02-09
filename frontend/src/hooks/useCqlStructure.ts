import { useState, useCallback, useRef, useEffect } from 'react'
import { useSelector } from 'react-redux'
import { cqlApi } from '../api'
import type { RootState } from '../store'
import type { TranslationMetadata } from '../types'

export interface CqlStructure {
  libraryId: string
  libraryVersion: string
  usings: string[]
  includes: string[]
  valueSets: string[]
  codes: string[]
  parameters: string[]
  expressions: { name: string; context?: string; resultType?: string }[]
  functions: { name: string; context?: string; resultType?: string }[]
}

const EMPTY_STRUCTURE: CqlStructure = {
  libraryId: '',
  libraryVersion: '',
  usings: [],
  includes: [],
  valueSets: [],
  codes: [],
  parameters: [],
  expressions: [],
  functions: [],
}

function metadataToStructure(meta: TranslationMetadata): CqlStructure {
  // Separate functions from definitions by detecting function signatures
  const expressions = meta.expressions.filter(
    (e) => e.accessLevel !== 'Private' && !e.name.startsWith('__')
  )
  // Functions have operand types in their resultType or are detected by naming pattern
  const defs = expressions.filter((e) => !e.resultType?.includes('->'))
  const funcs = expressions.filter((e) => e.resultType?.includes('->'))

  return {
    libraryId: meta.libraryId || '',
    libraryVersion: meta.libraryVersion || '',
    usings: meta.usings || [],
    includes: meta.includes || [],
    valueSets: meta.valueSets || [],
    codes: meta.codes || [],
    parameters: meta.parameters || [],
    expressions: defs.map((e) => ({ name: e.name, context: e.context, resultType: e.resultType })),
    functions: funcs.map((e) => ({ name: e.name, context: e.context, resultType: e.resultType })),
  }
}

export function useCqlStructure() {
  const cqlContent = useSelector((state: RootState) => state.editor.cqlContent)
  const [structure, setStructure] = useState<CqlStructure>(EMPTY_STRUCTURE)
  const [isParsing, setIsParsing] = useState(false)
  const [parseError, setParseError] = useState<string | null>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const lastParsedContent = useRef<string>('')

  const parseNow = useCallback(async (cql: string) => {
    if (!cql.trim()) {
      setStructure(EMPTY_STRUCTURE)
      return
    }
    setIsParsing(true)
    setParseError(null)
    try {
      const result = await cqlApi.translate({ cql })
      if (result.metadata) {
        setStructure(metadataToStructure(result.metadata))
      }
      lastParsedContent.current = cql
    } catch (err) {
      setParseError((err as Error).message)
    } finally {
      setIsParsing(false)
    }
  }, [])

  const parse = useCallback(() => {
    parseNow(cqlContent)
  }, [cqlContent, parseNow])

  // Auto-parse with debounce when content changes
  useEffect(() => {
    if (cqlContent === lastParsedContent.current) return
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => {
      parseNow(cqlContent)
    }, 2000)
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [cqlContent, parseNow])

  return { structure, isParsing, parseError, parse }
}

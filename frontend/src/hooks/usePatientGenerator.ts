import { useState, useCallback } from 'react'
import type {
  BatchGenerationConfig,
  CustomGenerationConfig,
  GeneratedPatientData,
} from '../config/twcore'
import {
  generateBatch,
  generateCustomPatient,
  downloadAsJson,
} from '../utils/fhirPatientGenerator'

interface UsePatientGeneratorReturn {
  results: GeneratedPatientData[]
  isGenerating: boolean
  generateBatchPatients: (config: BatchGenerationConfig) => void
  generateCustom: (config: CustomGenerationConfig) => void
  download: (filename?: string) => void
  clearResults: () => void
}

export function usePatientGenerator(): UsePatientGeneratorReturn {
  const [results, setResults] = useState<GeneratedPatientData[]>([])
  const [isGenerating, setIsGenerating] = useState(false)

  const generateBatchPatients = useCallback((config: BatchGenerationConfig) => {
    setIsGenerating(true)
    // Yield to UI thread before CPU-bound generation
    setTimeout(() => {
      const data = generateBatch(config)
      setResults(data)
      setIsGenerating(false)
    }, 0)
  }, [])

  const generateCustom = useCallback((config: CustomGenerationConfig) => {
    setIsGenerating(true)
    setTimeout(() => {
      const data = generateCustomPatient(config)
      setResults([data])
      setIsGenerating(false)
    }, 0)
  }, [])

  const download = useCallback(
    (filename?: string) => {
      if (results.length > 0) {
        downloadAsJson(results, filename)
      }
    },
    [results],
  )

  const clearResults = useCallback(() => setResults([]), [])

  return {
    results,
    isGenerating,
    generateBatchPatients,
    generateCustom,
    download,
    clearResults,
  }
}

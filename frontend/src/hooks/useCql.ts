import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useDispatch, useSelector } from 'react-redux'
import { cqlApi } from '../api'
import type { RootState } from '../store'
import {
  setElmJson,
  setErrors,
  setWarnings,
  setIsTranslating,
} from '../store/editorSlice'
import {
  setIsExecuting,
  setResults,
  setExecutionErrors,
  setExecutionTimeMs,
} from '../store/executionSlice'
import type { CqlTranslationRequest, CqlExecutionRequest } from '../types'

export function useTranslate() {
  const dispatch = useDispatch()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: CqlTranslationRequest) => {
      dispatch(setIsTranslating(true))
      return cqlApi.translate(request)
    },
    onSuccess: (data) => {
      dispatch(setIsTranslating(false))
      if (data.success) {
        dispatch(setElmJson(data.elmJson || null))
        dispatch(setErrors([]))
        dispatch(setWarnings(data.warnings || []))
      } else {
        dispatch(setElmJson(null))
        dispatch(setErrors(data.errors || []))
        dispatch(setWarnings(data.warnings || []))
      }
      queryClient.invalidateQueries({ queryKey: ['libraries'] })
    },
    onError: (error: Error) => {
      dispatch(setIsTranslating(false))
      dispatch(setErrors([{ severity: 'Error', message: error.message }]))
    },
  })
}

export function useValidate() {
  const dispatch = useDispatch()

  return useMutation({
    mutationFn: (cql: string) => {
      dispatch(setIsTranslating(true))
      return cqlApi.validate(cql)
    },
    onSuccess: (data) => {
      dispatch(setIsTranslating(false))
      dispatch(setErrors(data.errors || []))
      dispatch(setWarnings(data.warnings || []))
    },
    onError: (error: Error) => {
      dispatch(setIsTranslating(false))
      dispatch(setErrors([{ severity: 'Error', message: error.message }]))
    },
  })
}

export function useExecute() {
  const dispatch = useDispatch()

  return useMutation({
    mutationFn: (request: CqlExecutionRequest) => {
      dispatch(setIsExecuting(true))
      return cqlApi.execute(request)
    },
    onSuccess: (data) => {
      dispatch(setIsExecuting(false))
      if (data.success) {
        dispatch(setResults(data.results))
        dispatch(setExecutionErrors([]))
        dispatch(setExecutionTimeMs(data.metadata?.executionTimeMs || null))
      } else {
        dispatch(setResults({}))
        dispatch(setExecutionErrors(data.errors || ['Execution failed']))
      }
    },
    onError: (error: Error) => {
      dispatch(setIsExecuting(false))
      dispatch(setExecutionErrors([error.message]))
    },
  })
}

export function useLibraries(search?: string) {
  return useQuery({
    queryKey: ['libraries', search],
    queryFn: () => cqlApi.getLibraries(search),
  })
}

export function useLibrary(id: string | null) {
  return useQuery({
    queryKey: ['library', id],
    queryFn: () => (id ? cqlApi.getLibrary(id) : null),
    enabled: !!id,
  })
}

export function useCreateLibrary() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ cql, description }: { cql: string; description?: string }) =>
      cqlApi.createLibrary(cql, description),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['libraries'] })
    },
  })
}

export function useDeleteLibrary() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => cqlApi.deleteLibrary(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['libraries'] })
    },
  })
}

export function useCqlEditor() {
  const editor = useSelector((state: RootState) => state.editor)
  const execution = useSelector((state: RootState) => state.execution)

  const translateMutation = useTranslate()
  const validateMutation = useValidate()
  const executeMutation = useExecute()

  const translate = (cql: string) => {
    translateMutation.mutate({ cql })
  }

  const validate = (cql: string) => {
    validateMutation.mutate(cql)
  }

  const execute = (cql: string, patientId?: string, fhirServerUrl?: string) => {
    executeMutation.mutate({
      cql,
      patientId,
      fhirServerUrl,
    })
  }

  return {
    ...editor,
    ...execution,
    translate,
    validate,
    execute,
    isLoading: translateMutation.isPending || executeMutation.isPending,
  }
}

import type {
  MeasureEvaluationRequest,
  MeasureEvaluationResult,
  MeasureDefinition,
  MeasureAuditEntry,
  ValidationReport,
  MeasureReport,
  MeasureSchedule,
  MeasureComparisonResult,
  MeasureTrendResult,
  TestCase,
  TestCaseRunResult,
  CoverageResult,
  VersionComparison,
  BundleImportResult,
  DashboardSummary,
  BatchEvaluationRequest,
  BatchEvaluationResult,
  DataRequirementInfo,
  BatchTestCaseImportResult,
  EnhancedDashboardData,
  TrendSeriesPoint,
  ThresholdAlert,
  MeasureThreshold,
  QualityReportData,
} from '../types'
import { api } from './client'

const exportBlob = async (
  id: number,
  type: string,
  params?: Record<string, string>
): Promise<Blob> => {
  const response = await api.get(`/measures/${id}/export/${type}`, {
    params,
    responseType: 'blob',
  })
  return response.data
}

export const measureApi = {
  // Evaluation
  evaluate: async (request: MeasureEvaluationRequest): Promise<MeasureEvaluationResult> => {
    const response = await api.post<MeasureEvaluationResult>('/measures/evaluate', request)
    return response.data
  },

  evaluateMeasure: async (
    measureId: string,
    subject?: string,
    periodStart?: string,
    periodEnd?: string,
    fhirServerUrl?: string
  ): Promise<MeasureEvaluationResult> => {
    const params = new URLSearchParams()
    if (subject) params.append('subject', subject)
    if (periodStart) params.append('periodStart', periodStart)
    if (periodEnd) params.append('periodEnd', periodEnd)

    const response = await api.post<MeasureEvaluationResult>(
      `/measures/${measureId}/$evaluate-measure?${params.toString()}`,
      { fhirServerUrl }
    )
    return response.data
  },

  // Measure Definition CRUD
  getMeasures: async (search?: string, department?: string): Promise<MeasureDefinition[]> => {
    const params: Record<string, string> = {}
    if (search) params.search = search
    if (department) params.department = department
    const response = await api.get<MeasureDefinition[]>('/measures', { params })
    return response.data
  },

  getMeasure: async (id: number): Promise<MeasureDefinition> => {
    const response = await api.get<MeasureDefinition>(`/measures/${id}`)
    return response.data
  },

  createMeasure: async (definition: MeasureDefinition): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>('/measures', definition)
    return response.data
  },

  updateMeasure: async (id: number, definition: MeasureDefinition): Promise<MeasureDefinition> => {
    const response = await api.put<MeasureDefinition>(`/measures/${id}`, definition)
    return response.data
  },

  deleteMeasure: async (id: number): Promise<void> => {
    await api.delete(`/measures/${id}`)
  },

  // CQL Expressions (for population criteria mapping)
  getCqlExpressions: async (measureId: number): Promise<{ name: string; context: string; accessLevel: string; resultType: string | null }[]> => {
    const response = await api.get(`/measures/${measureId}/cql-expressions`)
    return response.data
  },

  // Data Requirements
  getDataRequirements: async (measureId: number): Promise<DataRequirementInfo[]> => {
    const response = await api.get<DataRequirementInfo[]>(`/measures/${measureId}/data-requirements`)
    return response.data
  },

  // FHIR Import/Export
  importFhirMeasure: async (fhirMeasure: unknown): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>('/measures/import/fhir', fhirMeasure)
    return response.data
  },

  exportFhirMeasure: async (id: number): Promise<unknown> => {
    const response = await api.get(`/measures/${id}/fhir`)
    return response.data
  },

  // Reports
  getReports: async (): Promise<MeasureReport[]> => {
    const response = await api.get<MeasureReport[]>('/measures/reports')
    return response.data
  },

  getReportsForMeasure: async (measureId: number): Promise<MeasureReport[]> => {
    const response = await api.get<MeasureReport[]>(`/measures/${measureId}/reports`)
    return response.data
  },

  getReport: async (reportId: number): Promise<MeasureReport> => {
    const response = await api.get<MeasureReport>(`/measures/reports/${reportId}`)
    return response.data
  },

  deleteReport: async (reportId: number): Promise<void> => {
    await api.delete(`/measures/reports/${reportId}`)
  },

  exportReport: async (reportId: number, format: string): Promise<Blob> => {
    const response = await api.get(`/measures/reports/${reportId}/export`, {
      params: { format },
      responseType: 'blob',
    })
    return response.data
  },

  // Schedules
  getSchedules: async (measureId: number): Promise<MeasureSchedule[]> => {
    const response = await api.get<MeasureSchedule[]>(`/measures/${measureId}/schedules`)
    return response.data
  },

  createSchedule: async (measureId: number, schedule: Partial<MeasureSchedule>): Promise<MeasureSchedule> => {
    const response = await api.post<MeasureSchedule>(`/measures/${measureId}/schedules`, schedule)
    return response.data
  },

  updateSchedule: async (scheduleId: number, schedule: Partial<MeasureSchedule>): Promise<MeasureSchedule> => {
    const response = await api.put<MeasureSchedule>(`/measures/schedules/${scheduleId}`, schedule)
    return response.data
  },

  deleteSchedule: async (scheduleId: number): Promise<void> => {
    await api.delete(`/measures/schedules/${scheduleId}`)
  },

  triggerSchedule: async (scheduleId: number): Promise<MeasureEvaluationResult> => {
    const response = await api.post<MeasureEvaluationResult>(`/measures/schedules/${scheduleId}/trigger`)
    return response.data
  },

  // Comparison & Trends
  comparePeriods: async (
    measureDefinitionId: number,
    measureName: string,
    p1Start: string,
    p1End: string,
    p2Start: string,
    p2End: string
  ): Promise<MeasureComparisonResult> => {
    const params = new URLSearchParams({
      measureDefinitionId: measureDefinitionId.toString(), measureName, p1Start, p1End, p2Start, p2End,
    })
    const response = await api.get<MeasureComparisonResult>(`/measures/compare?${params.toString()}`)
    return response.data
  },

  getTrend: async (measureDefinitionId: number, measureName: string, periods: number = 4): Promise<MeasureTrendResult> => {
    const params = new URLSearchParams({
      measureDefinitionId: measureDefinitionId.toString(), measureName, periods: periods.toString(),
    })
    const response = await api.get<MeasureTrendResult>(`/measures/trend?${params.toString()}`)
    return response.data
  },

  // Test Cases
  getTestCases: async (measureId: number): Promise<TestCase[]> => {
    const response = await api.get<TestCase[]>(`/measures/${measureId}/test-cases`)
    return response.data
  },

  getTestCase: async (measureId: number, testCaseId: number): Promise<TestCase> => {
    const response = await api.get<TestCase>(`/measures/${measureId}/test-cases/${testCaseId}`)
    return response.data
  },

  createTestCase: async (measureId: number, testCase: TestCase): Promise<TestCase> => {
    const response = await api.post<TestCase>(`/measures/${measureId}/test-cases`, testCase)
    return response.data
  },

  updateTestCase: async (measureId: number, testCaseId: number, testCase: TestCase): Promise<TestCase> => {
    const response = await api.put<TestCase>(`/measures/${measureId}/test-cases/${testCaseId}`, testCase)
    return response.data
  },

  deleteTestCase: async (measureId: number, testCaseId: number): Promise<void> => {
    await api.delete(`/measures/${measureId}/test-cases/${testCaseId}`)
  },

  runTestCase: async (measureId: number, testCaseId: number, debugMode = false): Promise<TestCaseRunResult> => {
    const response = await api.post<TestCaseRunResult>(
      `/measures/${measureId}/test-cases/${testCaseId}/run`,
      null,
      { params: { debugMode } }
    )
    return response.data
  },

  runAllTestCases: async (measureId: number, debugMode = false): Promise<TestCaseRunResult[]> => {
    const response = await api.post<TestCaseRunResult[]>(
      `/measures/${measureId}/test-cases/run`,
      null,
      { params: { debugMode } }
    )
    return response.data
  },

  runWithCoverage: async (measureId: number, testCaseId: number): Promise<CoverageResult> => {
    const response = await api.post<CoverageResult>(`/measures/${measureId}/test-cases/${testCaseId}/run-with-coverage`)
    return response.data
  },

  batchImportTestCases: async (
    measureId: number,
    testCases: TestCase[],
    dateShiftDays: number = 0
  ): Promise<BatchTestCaseImportResult> => {
    const response = await api.post<BatchTestCaseImportResult>(
      `/measures/${measureId}/test-cases/batch-import`,
      { testCases, dateShiftDays }
    )
    return response.data
  },

  // Version Management
  createMeasureVersion: async (id: number, type: string = 'minor'): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>(`/measures/${id}/version?type=${type}`)
    return response.data
  },

  getMeasureHistory: async (id: number): Promise<MeasureDefinition[]> => {
    const response = await api.get<MeasureDefinition[]>(`/measures/${id}/history`)
    return response.data
  },

  compareMeasureVersions: async (oldId: number, newId: number): Promise<VersionComparison> => {
    const response = await api.get<VersionComparison>(`/measures/version-compare?oldId=${oldId}&newId=${newId}`)
    return response.data
  },

  // Sharing & Permissions
  // PAT-117: `currentUser` was being sent as a request body field but the backend
  // derives it from the JWT via OwnershipVerifier, so the value was ignored.
  // Worse, the field broke deserialization when Jackson was configured to fail on
  // unknown properties. Removed — backend still works via JWT.
  shareMeasure: async (id: number, targetUsername: string): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>(`/measures/${id}/share`, { targetUsername })
    return response.data
  },

  unshareMeasure: async (id: number, targetUsername: string): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>(`/measures/${id}/unshare`, { targetUsername })
    return response.data
  },

  transferMeasureOwnership: async (id: number, newOwner: string): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>(`/measures/${id}/transfer`, { newOwner })
    return response.data
  },

  setMeasureAccessLevel: async (id: number, accessLevel: string): Promise<MeasureDefinition> => {
    const response = await api.put<MeasureDefinition>(`/measures/${id}/access`, { accessLevel })
    return response.data
  },

  getMeasuresByOwner: async (username: string): Promise<MeasureDefinition[]> => {
    const response = await api.get<MeasureDefinition[]>(`/measures/owner/${encodeURIComponent(username)}`)
    return response.data
  },

  getSharedMeasures: async (username: string): Promise<MeasureDefinition[]> => {
    const response = await api.get<MeasureDefinition[]>(`/measures/shared/${encodeURIComponent(username)}`)
    return response.data
  },

  // Workflow
  // PAT-117: backend derives the acting user from JWT (OwnershipVerifier), not
  // the request body. The vestigial `currentUser` body field triggered 500 errors
  // when Jackson rejected it as unknown on WorkflowActionRequest. Body is now
  // only used for the real payload (reason on reject).
  submitForReview: async (id: number): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>(`/measures/${id}/submit-for-review`, {})
    return response.data
  },

  approveMeasure: async (id: number): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>(`/measures/${id}/approve`, {})
    return response.data
  },

  rejectMeasure: async (id: number, reason?: string): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>(`/measures/${id}/reject`, { reason })
    return response.data
  },

  retireMeasure: async (id: number): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>(`/measures/${id}/retire`, {})
    return response.data
  },

  // Locking
  lockMeasure: async (id: number): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>(`/measures/${id}/lock`, {})
    return response.data
  },

  unlockMeasure: async (id: number): Promise<MeasureDefinition> => {
    const response = await api.post<MeasureDefinition>(`/measures/${id}/unlock`, {})
    return response.data
  },

  // Validation
  validateMeasure: async (id: number): Promise<ValidationReport> => {
    const response = await api.post<ValidationReport>(`/measures/${id}/validate`)
    return response.data
  },

  quickValidateMeasure: async (id: number): Promise<ValidationReport> => {
    const response = await api.post<ValidationReport>(`/measures/${id}/validate/quick`)
    return response.data
  },

  // Audit Trail
  getAuditTrail: async (id: number): Promise<MeasureAuditEntry[]> => {
    const response = await api.get<MeasureAuditEntry[]>(`/measures/${id}/audit`)
    return response.data
  },

  // Bundle Export/Import
  exportBundle: (id: number, format: string = 'json'): Promise<Blob> =>
    exportBlob(id, 'bundle', { format }),
  exportCql: (id: number): Promise<Blob> => exportBlob(id, 'cql'),
  exportElm: (id: number): Promise<Blob> => exportBlob(id, 'elm'),
  exportHqmf: (id: number): Promise<Blob> => exportBlob(id, 'hqmf'),
  exportHumanReadable: (id: number): Promise<Blob> => exportBlob(id, 'human-readable'),

  importBundle: async (json: unknown): Promise<BundleImportResult> => {
    const response = await api.post<BundleImportResult>('/measures/import/bundle', json)
    return response.data
  },

  // Dashboard
  getDashboard: async (): Promise<DashboardSummary> => {
    const response = await api.get<DashboardSummary>('/measures/dashboard')
    return response.data
  },

  // Batch Evaluation
  batchEvaluate: async (request: BatchEvaluationRequest): Promise<BatchEvaluationResult> => {
    const response = await api.post<BatchEvaluationResult>('/measures/batch-evaluate', request)
    return response.data
  },

  // Enhanced Dashboard
  getEnhancedDashboard: async (department?: string): Promise<EnhancedDashboardData> => {
    const response = await api.get<EnhancedDashboardData>('/measures/dashboard/enhanced', {
      params: department ? { department } : undefined,
    })
    return response.data
  },

  getDashboardTrends: async (measureId?: number, periodType = 'monthly', count = 12): Promise<TrendSeriesPoint[]> => {
    const response = await api.get<TrendSeriesPoint[]>('/measures/dashboard/trends', {
      params: { measureId, periodType, count },
    })
    return response.data
  },

  getDepartmentDrilldown: async (code: string): Promise<Record<string, unknown>> => {
    const response = await api.get<Record<string, unknown>>(`/measures/dashboard/department/${code}`)
    return response.data
  },

  getDashboardAlerts: async (department?: string): Promise<ThresholdAlert[]> => {
    const response = await api.get<ThresholdAlert[]>('/measures/dashboard/alerts', {
      params: department ? { department } : undefined,
    })
    return response.data
  },

  setThreshold: async (measureId: number, threshold: MeasureThreshold): Promise<MeasureThreshold> => {
    const response = await api.post<MeasureThreshold>(`/measures/${measureId}/thresholds`, threshold)
    return response.data
  },

  getThresholds: async (measureId: number): Promise<MeasureThreshold[]> => {
    const response = await api.get<MeasureThreshold[]>(`/measures/${measureId}/thresholds`)
    return response.data
  },

  getQualityReport: async (type = 'monthly', department?: string): Promise<QualityReportData> => {
    const response = await api.get<QualityReportData>('/measures/dashboard/report', {
      params: { type, department },
    })
    return response.data
  },
}

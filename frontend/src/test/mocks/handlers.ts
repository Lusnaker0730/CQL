import { http, HttpResponse } from 'msw'

export const handlers = [
  // Auth
  http.post('/api/auth/login', () => {
    return HttpResponse.json({
      token: 'mock-jwt-token',
      username: 'testuser',
      role: 'USER',
      expiresIn: 3600000,
    })
  }),

  http.post('/api/auth/register', () => {
    return HttpResponse.json({
      token: 'mock-jwt-token',
      username: 'newuser',
      role: 'USER',
      expiresIn: 3600000,
    })
  }),

  http.get('/api/auth/me', () => {
    return HttpResponse.json({
      username: 'testuser',
      email: 'test@example.com',
      role: 'USER',
    })
  }),

  // CQL
  http.post('/api/cql/translate', () => {
    return HttpResponse.json({
      success: true,
      elmJson: '{}',
      errors: [],
      warnings: [],
      metadata: { libraryId: 'Test', libraryVersion: '1.0' },
    })
  }),

  http.post('/api/cql/validate', () => {
    return HttpResponse.json({
      success: true,
      errors: [],
      warnings: [],
    })
  }),

  http.post('/api/cql/execute', () => {
    return HttpResponse.json({
      success: true,
      results: {},
      metadata: { executionTimeMs: 100 },
    })
  }),

  http.get('/api/cql/libraries', () => {
    return HttpResponse.json([])
  }),

  // CDS Hooks
  http.get('/cds-services', () => {
    return HttpResponse.json({
      services: [
        { id: 'test-service', hook: 'patient-view', title: 'Test Service', description: 'A test service' },
      ],
    })
  }),

  http.post('/cds-services/:serviceId', () => {
    return HttpResponse.json({
      cards: [{ uuid: '1', summary: 'Test Card', indicator: 'info', source: { label: 'Test' } }],
    })
  }),

  http.get('/api/cds/services', () => {
    return HttpResponse.json([])
  }),

  // Measures
  http.post('/api/measures/evaluate', () => {
    return HttpResponse.json({
      measureId: 'test',
      status: 'complete',
      groups: [],
    })
  }),

  // FHIR
  http.get('/api/fhir/:resourceType', () => {
    return HttpResponse.json({ resourceType: 'Bundle', type: 'searchset', entry: [] })
  }),
]

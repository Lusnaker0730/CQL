/**
 * Shared field constraints — aligned with backend Jakarta Bean Validation annotations.
 *
 * When the backend @Size(max=N) changes, update here so both sides stay in sync.
 * Ref: backend model classes in com.cqlplatform.model.* and com.cqlplatform.entity.*
 */

// ─── Measure ────────────────────────────────────────────────────────────────────
export const MEASURE = {
  name:             { maxLength: 200,   required: true },
  version:          { maxLength: 50 },
  title:            { maxLength: 500 },
  description:      { maxLength: 5000 },
  rationale:        { maxLength: 5000 },
  clinicalGuidance: { maxLength: 5000 },
  steward:          { maxLength: 500 },
  developer:        { maxLength: 200 },
  copyright:        { maxLength: 5000 },
  disclaimer:       { maxLength: 5000 },
  measureSet:       { maxLength: 200 },
  nqfNumber:        { maxLength: 50 },
  cmsMeasureId:     { maxLength: 50 },
  reference:        { maxLength: 2000 },
  cqlContent:       { maxBytes: 524_288 }, // 512 KB
} as const

// ─── CDS Artifact ───────────────────────────────────────────────────────────────
export const ARTIFACT = {
  name:        { maxLength: 255,  required: true },
  version:     { maxLength: 50 },
  description: { maxLength: 5000 },
  publisher:   { maxLength: 255 },
  purpose:     { maxLength: 5000 },
  copyright:   { maxLength: 5000 },
  url:         { maxLength: 500 },
} as const

// ─── eCQM Artifact ──────────────────────────────────────────────────────────────
export const ECQM = {
  name:        { maxLength: 255,  required: true },
  description: { maxLength: 5000 },
} as const

// ─── CDS Service Config ────────────────────────────────────────────────────────
export const CDS_SERVICE = {
  id:          { maxLength: 100,  required: true },
  title:       { maxLength: 200,  required: true },
  description: { maxLength: 1000 },
  cqlContent:  { maxBytes: 524_288 }, // 512 KB
} as const

// ─── EHR Connection ─────────────────────────────────────────────────────────────
export const EHR_CONNECTION = {
  name:          { maxLength: 255,  required: true },
  fhirServerUrl: { maxLength: 2048, required: true },
  description:   { maxLength: 1000 },
  credentials:   { maxLength: 10000 },
  tokenEndpoint: { maxLength: 500 },
} as const

// ─── Auth ───────────────────────────────────────────────────────────────────────
export const AUTH = {
  username: { minLength: 3, maxLength: 50, required: true },
  password: { minLength: 8, maxLength: 100, required: true },
  email:    { maxLength: 200 },
} as const

// ─── CQL Library ────────────────────────────────────────────────────────────────
export const CQL_LIBRARY = {
  description: { maxLength: 2000 },
  cql:         { maxBytes: 524_288 }, // 512 KB
} as const

// ─── Test Case ──────────────────────────────────────────────────────────────────
export const TEST_CASE = {
  title:       { maxLength: 200,  required: true },
  description: { maxLength: 2000 },
  series:      { maxLength: 100 },
  patientBundleJson: { maxBytes: 2_097_152 }, // 2 MB
} as const

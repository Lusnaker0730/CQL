/**
 * FHIR Bundle constants extracted out of {@code components/testcase-builder/
 * constants.tsx} so consumers (e.g. {@code BundleBuilderContext}, tests) can
 * import them without transitively pulling the MUI icons barrel — that barrel
 * blows past Windows file-handle limits in vitest (EMFILE) and prevents tests
 * from collecting (PAT-149).
 */
export const FHIR_BUNDLE_TYPE = 'collection'

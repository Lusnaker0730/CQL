export const helpContent = {
  editor: {
    translate: 'Translate CQL to ELM (Expression Logical Model). Validates syntax and semantics. Shortcut: Ctrl+S',
    save: 'Save the current CQL content as a versioned library in the platform database.',
    export: 'Export the library as a FHIR Library resource (JSON format) for interoperability.',
    import: 'Import a FHIR Library resource (JSON) and load its CQL content into the editor.',
  },
  cds: {
    hookType: 'The CDS hook defines when this service is triggered in the clinical workflow. "patient-view" fires when a patient chart is opened, "order-select" fires during order entry.',
    prefetch: 'Prefetch templates define FHIR queries the EHR should execute before calling your service. Use FHIR search URLs with {{context.patientId}} placeholders.',
  },
  measures: {
    scoringType: 'Proportion: ratio of numerator/denominator. Continuous Variable: statistical measure of population values. Cohort: simple count of population members.',
    details: 'Define the measure metadata: name, version, status, and scoring type. These fields map to FHIR Measure resource attributes.',
    cql: 'Write or edit the CQL logic that powers this measure. Translate to validate syntax, then save to persist changes.',
    populationCriteria: 'Map CQL expressions to measure populations (Initial Population, Denominator, Numerator, etc.). Each population references a CQL expression that returns true/false for patient membership.',
    evaluate: 'Run this measure against patient data from a FHIR server. Results show population counts, individual patient membership, and optional stratifiers.',
    testCases: 'Create patient test cases with expected population outcomes. Run tests to verify your measure logic produces correct results before deployment.',
    reports: 'View historical evaluation reports for this measure. Compare results across runs to track changes over time.',
  },
  fhir: {
    serverUrl: 'The base URL of a FHIR R4 server. Default is the public HAPI FHIR server. You can point to your own server for testing.',
    bulkExport: 'Initiate a FHIR Bulk Data Export ($export). System exports all resources; Patient/Group exports scope to a compartment. Results are NDJSON files.',
  },
  quickStart: [
    {
      title: 'Getting Started',
      content:
        'Write CQL (Clinical Quality Language) in the editor, click "Translate" to validate, then "Execute" to run against patient data from a FHIR server.',
    },
    {
      title: 'CQL Basics',
      content:
        'CQL libraries start with a library declaration and FHIR model. Define value sets, patient context, and expressions to compute clinical logic.',
    },
    {
      title: 'CDS Hooks',
      content:
        'Create CDS services that trigger at clinical decision points. Your CQL logic runs automatically and returns advisory cards to clinicians.',
    },
    {
      title: 'Quality Measures',
      content:
        'Define eCQM (electronic Clinical Quality Measures) using CQL. Evaluate measure populations against patient data and generate reports.',
    },
    {
      title: 'FHIR Browser',
      content:
        'Browse resources on any FHIR R4 server. Search by resource type and parameters, or read individual resources by ID.',
    },
    {
      title: 'Keyboard Shortcuts',
      content: 'Ctrl+S: Translate CQL | Ctrl+Enter: Execute CQL | Standard Monaco editor shortcuts available.',
    },
  ],
}

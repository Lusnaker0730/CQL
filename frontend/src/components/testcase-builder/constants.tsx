import {
  Person,
  LocalHospital,
  Healing,
  MonitorHeart,
  ContentCut,
  Medication,
  HealthAndSafety,
  Biotech,
  Vaccines,
  ReportProblem,
  Assignment,
  EventNote,
  Flag,
  People,
  DevicesOther,
  MedicalServices,
  Favorite,
  Visibility,
  Receipt,
  NoteAlt,
  Bloodtype,
} from '@mui/icons-material'

// --- Resource Icons (shared by AddResourceButton & ResourceEntryList) ---

export const RESOURCE_ICONS: Record<string, React.ReactElement> = {
  Patient: <Person fontSize="small" />,
  Encounter: <LocalHospital fontSize="small" />,
  Condition: <Healing fontSize="small" />,
  Observation: <MonitorHeart fontSize="small" />,
  Procedure: <ContentCut fontSize="small" />,
  MedicationRequest: <Medication fontSize="small" />,
  MedicationAdministration: <Medication fontSize="small" />,
  MedicationStatement: <Medication fontSize="small" />,
  MedicationDispense: <Medication fontSize="small" />,
  Coverage: <HealthAndSafety fontSize="small" />,
  DiagnosticReport: <Biotech fontSize="small" />,
  Immunization: <Vaccines fontSize="small" />,
  AllergyIntolerance: <ReportProblem fontSize="small" />,
  ServiceRequest: <Assignment fontSize="small" />,
  CarePlan: <EventNote fontSize="small" />,
  Goal: <Flag fontSize="small" />,
  FamilyMemberHistory: <People fontSize="small" />,
  DeviceUseStatement: <DevicesOther fontSize="small" />,
  Device: <DevicesOther fontSize="small" />,
  CareTeam: <People fontSize="small" />,
  Specimen: <Bloodtype fontSize="small" />,
  Claim: <Receipt fontSize="small" />,
  DocumentReference: <NoteAlt fontSize="small" />,
  Composition: <NoteAlt fontSize="small" />,
  Consent: <Visibility fontSize="small" />,
  RelatedPerson: <People fontSize="small" />,
  Practitioner: <Person fontSize="small" />,
  PractitionerRole: <Person fontSize="small" />,
  Organization: <LocalHospital fontSize="small" />,
  Location: <LocalHospital fontSize="small" />,
  QuestionnaireResponse: <NoteAlt fontSize="small" />,
  RiskAssessment: <ReportProblem fontSize="small" />,
  DetectedIssue: <ReportProblem fontSize="small" />,
  AdverseEvent: <ReportProblem fontSize="small" />,
  NutritionOrder: <Favorite fontSize="small" />,
}

export const DEFAULT_RESOURCE_ICON = <MedicalServices fontSize="small" />

export function getResourceIcon(resourceType: string): React.ReactElement {
  return RESOURCE_ICONS[resourceType] || DEFAULT_RESOURCE_ICON
}

// --- FHIR Constants ---

export const FHIR_UCUM_SYSTEM = 'http://unitsofmeasure.org'
export const FHIR_BUNDLE_TYPE = 'collection'

// --- UI Strings (centralized for future i18n) ---

export const STRINGS = {
  // VisualBundleBuilder
  bundleEntries: 'BUNDLE ENTRIES',
  buildTestBundle: 'Build your test patient bundle',
  stepPatient: '1. Patient',
  stepEncounter: '2. Encounter',
  stepClinical: '3. Clinical Data',
  startGuide:
    'Start by adding a Patient, then add Encounters and clinical resources (Conditions, Observations, Procedures, etc.) to build the test scenario.',
  selectResource: 'Select a resource from the list to edit its properties.',

  // AddResourceButton
  addResource: 'Add Resource',

  // ResourceEntryList
  noResourcesYet: 'No resources yet. Start by adding a Patient resource.',
  deleteResource: 'Delete Resource',
  deleteConfirm: 'Are you sure you want to remove this resource from the test case bundle?',
  cancel: 'Cancel',
  delete: 'Delete',

  // ResourceForm
  requiredFields: 'Required Fields',
  optionalFields: 'Optional Fields',
  addAttribute: 'Add Attribute',
  addAttributes: 'Add Attributes',

  // TwcoreCodePicker
  twcoreTerminology: 'TWCORE Terminology',
  searchCodePlaceholder: 'Search code, display, or Chinese name...',
  noMatchingCodes: 'No matching codes found.',
  noTerminology: 'No TWCORE terminology available.',
  use: 'Use',

  // QuantityField / IdentifierField placeholders
  ucumSystemPlaceholder: FHIR_UCUM_SYSTEM,
  identifierSystemPlaceholder: 'e.g. http://hospital.org/mrn',
} as const

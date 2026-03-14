/* eslint-disable react-refresh/only-export-components -- constants file, no components */
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


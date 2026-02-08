// Taiwan Core Data for Interoperability (TWCDI) - 20 Clinical Data Classes
// Mapped to TW Core FHIR R4 Profiles

export interface TwcdiDataClass {
  name: string
  fhirResource: string
  profileUrl: string
  description: string
}

export const TWCDI_DATA_CLASSES: TwcdiDataClass[] = [
  { name: 'Patient', fhirResource: 'Patient', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Patient-twcore', description: 'Patient demographics and identifiers' },
  { name: 'Encounter', fhirResource: 'Encounter', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Encounter-twcore', description: 'Healthcare encounters and visits' },
  { name: 'Condition', fhirResource: 'Condition', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Condition-twcore', description: 'Clinical conditions and diagnoses' },
  { name: 'Observation - Vital Signs', fhirResource: 'Observation', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Observation-vitalSigns-twcore', description: 'Vital sign measurements (BP, HR, Temp, etc.)' },
  { name: 'Observation - Laboratory', fhirResource: 'Observation', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Observation-laboratoryResult-twcore', description: 'Laboratory test results' },
  { name: 'Observation - BMI', fhirResource: 'Observation', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Observation-bmi-twcore', description: 'Body Mass Index observations' },
  { name: 'Observation - Blood Pressure', fhirResource: 'Observation', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Observation-bloodPressure-twcore', description: 'Blood pressure measurements (systolic/diastolic)' },
  { name: 'MedicationRequest', fhirResource: 'MedicationRequest', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/MedicationRequest-twcore', description: 'Medication orders and prescriptions' },
  { name: 'Medication', fhirResource: 'Medication', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Medication-twcore', description: 'Medication information' },
  { name: 'MedicationStatement', fhirResource: 'MedicationStatement', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/MedicationStatement-twcore', description: 'Record of medication usage' },
  { name: 'Procedure', fhirResource: 'Procedure', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Procedure-twcore', description: 'Clinical procedures' },
  { name: 'DiagnosticReport', fhirResource: 'DiagnosticReport', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/DiagnosticReport-twcore', description: 'Diagnostic reports' },
  { name: 'AllergyIntolerance', fhirResource: 'AllergyIntolerance', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/AllergyIntolerance-twcore', description: 'Allergy and intolerance records' },
  { name: 'Immunization', fhirResource: 'Immunization', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Immunization-twcore', description: 'Immunization records' },
  { name: 'Organization', fhirResource: 'Organization', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Organization-twcore', description: 'Healthcare organizations' },
  { name: 'Practitioner', fhirResource: 'Practitioner', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Practitioner-twcore', description: 'Healthcare practitioners' },
  { name: 'ImagingStudy', fhirResource: 'ImagingStudy', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/ImagingStudy-twcore', description: 'Imaging studies (DICOM)' },
  { name: 'Location', fhirResource: 'Location', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Location-twcore', description: 'Physical locations' },
  { name: 'Composition', fhirResource: 'Composition', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Composition-twcore', description: 'Clinical document composition' },
  { name: 'DocumentReference', fhirResource: 'DocumentReference', profileUrl: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/DocumentReference-twcore', description: 'Document references' },
]

// Taiwan-relevant code system URIs
export const TW_CODE_SYSTEMS = {
  LOINC: 'http://loinc.org',
  SNOMED_CT: 'http://snomed.info/sct',
  ICD_10_CM_TW: 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-cm-2023-tw',
  ICD_10_PCS_TW: 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-pcs-2023-tw',
  NHI_MEDICATION: 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medication-nhi-tw',
  NHI_PROCEDURE: 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medical-service-payment-tw',
  TWMedicalDepartment: 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/department-tw',
} as const

// Common LOINC codes used in TW Core vital signs & labs
export const TW_COMMON_LOINC_CODES = {
  // Vital Signs
  BLOOD_PRESSURE_PANEL: { code: '85354-9', display: 'Blood Pressure Panel' },
  SYSTOLIC_BP: { code: '8480-6', display: 'Systolic Blood Pressure' },
  DIASTOLIC_BP: { code: '8462-4', display: 'Diastolic Blood Pressure' },
  HEART_RATE: { code: '8867-4', display: 'Heart Rate' },
  BODY_TEMPERATURE: { code: '8310-5', display: 'Body Temperature' },
  RESPIRATORY_RATE: { code: '9279-1', display: 'Respiratory Rate' },
  BODY_WEIGHT: { code: '29463-7', display: 'Body Weight' },
  BODY_HEIGHT: { code: '8302-2', display: 'Body Height' },
  BMI: { code: '39156-5', display: 'Body Mass Index' },
  SPO2: { code: '2708-6', display: 'Oxygen Saturation (SpO2)' },
  // Laboratory
  HEMOGLOBIN_A1C: { code: '4548-4', display: 'Hemoglobin A1c' },
  GLUCOSE: { code: '2345-7', display: 'Glucose [Mass/volume] in Serum or Plasma' },
  EGFR: { code: '48642-3', display: 'eGFR (CKD-EPI)' },
  TOTAL_CHOLESTEROL: { code: '2093-3', display: 'Total Cholesterol' },
  LDL: { code: '2089-1', display: 'LDL Cholesterol' },
  HDL: { code: '2085-9', display: 'HDL Cholesterol' },
  TRIGLYCERIDES: { code: '2571-8', display: 'Triglycerides' },
  WBC: { code: '6690-2', display: 'White Blood Cells' },
  RBC: { code: '789-8', display: 'Red Blood Cells' },
  HEMOGLOBIN: { code: '718-7', display: 'Hemoglobin' },
  PLATELETS: { code: '777-3', display: 'Platelets' },
  CREATININE: { code: '2160-0', display: 'Creatinine' },
  ALT: { code: '1742-6', display: 'ALT (GPT)' },
  AST: { code: '1920-8', display: 'AST (GOT)' },
} as const

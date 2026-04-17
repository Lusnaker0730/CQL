import {
  allConditions,
  allObservations,
  allMedications,
  allAllergies,
} from '../config/twcore'
import type {
  ConditionItem,
  ObservationItem,
  MedicationItem,
  AllergyItem,
  FhirResource,
  GeneratedPatientData,
  BatchGenerationConfig,
  CustomGenerationConfig,
} from '../config/twcore'
import {
  generateNhiId,
  generateName,
  generateAddress,
  generateMobile,
  generateLandline,
  generateBirthDate,
  generateEmail,
  randomDateInRange,
  randomGender,
} from './twDemographics'
import { randomInt, randomElement, randomFloat, pickRandom } from './random'
import { downloadBlob } from './download'

const FHIR_SNOMED = 'http://snomed.info/sct'
const FHIR_LOINC = 'http://loinc.org'
const ENCOUNTER_TYPES = [
  { code: 'AMB', display: 'ambulatory', weight: 0.6 },
  { code: 'EMER', display: 'emergency', weight: 0.2 },
  { code: 'IMP', display: 'inpatient encounter', weight: 0.2 },
] as const

const DOSAGE_FORM_CODES: Record<string, string> = {
  tablet: '385055001',
  capsule: '385049006',
  cream: '385101003',
  inhaler: '420317006',
  eye_drops: '385023001',
}

const MEDICATION_INSTRUCTIONS = [
  { text: '每日一次，飯後服用', frequency: 1, period: 1, periodUnit: 'd' },
  { text: '每日兩次，早晚飯後服用', frequency: 2, period: 1, periodUnit: 'd' },
  { text: '每日三次，飯後服用', frequency: 3, period: 1, periodUnit: 'd' },
  { text: '每日四次，每六小時服用', frequency: 4, period: 1, periodUnit: 'd' },
  { text: '需要時服用', frequency: 1, period: 1, periodUnit: 'd' },
  { text: '睡前服用', frequency: 1, period: 1, periodUnit: 'd' },
  { text: '每週一次', frequency: 1, period: 1, periodUnit: 'wk' },
] as const

// Lazy-initialized lookup maps for O(1) code resolution
let _conditionMap: Map<string, ConditionItem> | null = null
let _observationMap: Map<string, ObservationItem> | null = null
let _medicationMap: Map<string, MedicationItem> | null = null
let _allergyMap: Map<string, AllergyItem> | null = null

function getConditionMap(): Map<string, ConditionItem> {
  return (_conditionMap ??= new Map(allConditions.map((c) => [c.code, c])))
}
function getObservationMap(): Map<string, ObservationItem> {
  return (_observationMap ??= new Map(allObservations.map((o) => [o.code, o])))
}
function getMedicationMap(): Map<string, MedicationItem> {
  return (_medicationMap ??= new Map(allMedications.map((m) => [m.code, m])))
}
function getAllergyMap(): Map<string, AllergyItem> {
  return (_allergyMap ??= new Map(allAllergies.map((a) => [a.code, a])))
}

let idCounter = 0

function nextId(prefix: string): string {
  idCounter += 1
  return `${prefix}-${Date.now()}-${idCounter}`
}

function pickWeightedEncounterType(): (typeof ENCOUNTER_TYPES)[number] {
  const r = Math.random()
  let cumulative = 0
  for (const et of ENCOUNTER_TYPES) {
    cumulative += et.weight
    if (r <= cumulative) return et
  }
  return ENCOUNTER_TYPES[0]
}

function generateMedicationPair(
  patientId: string,
  item: MedicationItem,
  dateFrom?: string,
  dateTo?: string,
): { medication: FhirResource; request: FhirResource } {
  const med = generateMedication(item)
  const request = generateMedicationRequest(patientId, item, dateFrom, dateTo)
  return { medication: med, request }
}

export function generatePatient(): FhirResource {
  const gender = randomGender()
  const { family, given } = generateName(gender)
  const address = generateAddress()
  const birthDate = generateBirthDate()
  const nhiId = generateNhiId(gender)
  const mobile = generateMobile()
  const landline = generateLandline()
  const email = generateEmail(family, given)
  const id = nextId('pat')

  return {
    resourceType: 'Patient',
    id,
    meta: {
      profile: ['https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Patient-twcore'],
    },
    identifier: [
      {
        use: 'official',
        type: {
          coding: [{ system: 'http://terminology.hl7.org/CodeSystem/v2-0203', code: 'NNxxx', display: 'National Person Identifier' }],
        },
        system: 'http://www.moi.gov.tw/',
        value: nhiId,
      },
    ],
    active: true,
    name: [
      {
        use: 'official',
        family,
        given: [given],
        text: `${family}${given}`,
      },
    ],
    telecom: [
      { system: 'phone', value: mobile, use: 'mobile' },
      { system: 'phone', value: landline, use: 'home' },
      { system: 'email', value: email, use: 'home' },
    ],
    gender,
    birthDate,
    address: [
      {
        use: 'home',
        type: 'both',
        text: `${address.postalCode} ${address.city}${address.district}${address.line}`,
        line: [address.line],
        city: address.city,
        district: address.district,
        postalCode: address.postalCode,
        country: 'TW',
      },
    ],
    communication: [
      {
        language: {
          coding: [{ system: 'urn:ietf:bcp:47', code: 'zh-TW', display: 'Chinese (Taiwan)' }],
          text: '中文',
        },
        preferred: true,
      },
    ],
  }
}

export function generateEncounter(
  patientId: string,
  dateFrom?: string,
  dateTo?: string,
): FhirResource {
  const encounterType = pickWeightedEncounterType()
  const date = randomDateInRange(dateFrom, dateTo, 180)
  const id = nextId('enc')

  return {
    resourceType: 'Encounter',
    id,
    status: 'finished',
    class: {
      system: 'http://terminology.hl7.org/CodeSystem/v3-ActCode',
      code: encounterType.code,
      display: encounterType.display,
    },
    subject: { reference: `Patient/${patientId}` },
    period: {
      start: `${date}T09:00:00+08:00`,
      end: `${date}T10:00:00+08:00`,
    },
  }
}

export function generateCondition(
  patientId: string,
  item: ConditionItem,
  dateFrom?: string,
  dateTo?: string,
): FhirResource {
  const date = randomDateInRange(dateFrom, dateTo, 730)
  const id = nextId('cond')

  return {
    resourceType: 'Condition',
    id,
    clinicalStatus: {
      coding: [{ system: 'http://terminology.hl7.org/CodeSystem/condition-clinical', code: 'active', display: 'Active' }],
    },
    verificationStatus: {
      coding: [{ system: 'http://terminology.hl7.org/CodeSystem/condition-ver-status', code: 'confirmed', display: 'Confirmed' }],
    },
    code: {
      coding: [{ system: item.system, code: item.code, display: item.display }],
      text: item.display,
    },
    subject: { reference: `Patient/${patientId}` },
    onsetDateTime: `${date}T00:00:00+08:00`,
    recordedDate: date,
  }
}

export function generateObservation(
  patientId: string,
  item: ObservationItem,
  dateFrom?: string,
  dateTo?: string,
): FhirResource {
  const date = randomDateInRange(dateFrom, dateTo, 30)
  const value = randomFloat(item.min_val, item.max_val, item.min_val % 1 !== 0 ? 2 : 1)
  const id = nextId('obs')

  return {
    resourceType: 'Observation',
    id,
    status: 'final',
    category: [
      {
        coding: [{
          system: 'http://terminology.hl7.org/CodeSystem/observation-category',
          code: 'vital-signs',
          display: 'Vital Signs',
        }],
      },
    ],
    code: {
      coding: [{ system: FHIR_LOINC, code: item.code, display: item.display }],
      text: item.display,
    },
    subject: { reference: `Patient/${patientId}` },
    effectiveDateTime: `${date}T${String(randomInt(8, 17)).padStart(2, '0')}:${String(randomInt(0, 59)).padStart(2, '0')}:00+08:00`,
    valueQuantity: {
      value,
      unit: item.unit,
      system: 'http://unitsofmeasure.org',
      code: item.ucum_code,
    },
  }
}

export function generateMedication(item: MedicationItem): FhirResource {
  const id = nextId('med')
  const formCode = DOSAGE_FORM_CODES[item.dosage_form] ?? '385055001'

  return {
    resourceType: 'Medication',
    id,
    code: {
      coding: [{ system: item.system, code: item.code, display: item.display }],
      text: item.display,
    },
    form: {
      coding: [{
        system: FHIR_SNOMED,
        code: formCode,
        display: item.dosage_form,
      }],
    },
  }
}

export function generateMedicationRequest(
  patientId: string,
  item: MedicationItem,
  dateFrom?: string,
  dateTo?: string,
): FhirResource {
  const date = randomDateInRange(dateFrom, dateTo, 90)
  const instruction = randomElement(MEDICATION_INSTRUCTIONS)
  const id = nextId('medreq')

  // Build medication coding with both RxNorm and ATC codes for CQL compatibility
  const medicationCoding: Array<{system: string; code: string; display: string}> = [
    { system: item.system, code: item.code, display: item.display },
  ]
  if (item.atc) {
    medicationCoding.push({
      system: 'http://www.whocc.no/atc',
      code: item.atc,
      display: item.display,
    })
  }

  return {
    resourceType: 'MedicationRequest',
    id,
    status: 'active',
    intent: 'order',
    medicationCodeableConcept: {
      coding: medicationCoding,
      text: item.display,
    },
    subject: { reference: `Patient/${patientId}` },
    authoredOn: date,
    dosageInstruction: [
      {
        text: instruction.text,
        timing: {
          repeat: {
            frequency: instruction.frequency,
            period: instruction.period,
            periodUnit: instruction.periodUnit,
          },
        },
      },
    ],
  }
}

export function generateAllergyIntolerance(
  patientId: string,
  item: AllergyItem,
  dateFrom?: string,
  dateTo?: string,
): FhirResource {
  const date = randomDateInRange(dateFrom, dateTo, 730)
  const id = nextId('allergy')

  return {
    resourceType: 'AllergyIntolerance',
    id,
    clinicalStatus: {
      coding: [{ system: 'http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical', code: 'active', display: 'Active' }],
    },
    verificationStatus: {
      coding: [{ system: 'http://terminology.hl7.org/CodeSystem/allergyintolerance-verification', code: 'confirmed', display: 'Confirmed' }],
    },
    type: 'allergy',
    category: [item.type],
    criticality: item.criticality,
    code: {
      coding: [{ system: item.system, code: item.code, display: item.display }],
      text: item.display,
    },
    patient: { reference: `Patient/${patientId}` },
    onsetDateTime: `${date}T00:00:00+08:00`,
    recordedDate: date,
  }
}

export function generateCompletePatient(config: BatchGenerationConfig): GeneratedPatientData {
  const patient = generatePatient()
  const patientId = patient.id as string
  const { dateFrom, dateTo } = config

  const encounters = Array.from({ length: config.numEncounters }, () =>
    generateEncounter(patientId, dateFrom, dateTo),
  )

  const conditions = pickRandom(allConditions, config.numConditions).map((item) =>
    generateCondition(patientId, item, dateFrom, dateTo),
  )

  const observations = pickRandom(allObservations, config.numObservations).map((item) =>
    generateObservation(patientId, item, dateFrom, dateTo),
  )

  const medPairs = pickRandom(allMedications, config.numMedications).map((item) =>
    generateMedicationPair(patientId, item, dateFrom, dateTo),
  )

  const allergies = pickRandom(allAllergies, config.numAllergies).map((item) =>
    generateAllergyIntolerance(patientId, item, dateFrom, dateTo),
  )

  return {
    patient,
    encounters,
    conditions,
    observations,
    medications: medPairs.map((p) => p.medication),
    medication_requests: medPairs.map((p) => p.request),
    allergies,
  }
}

export function generateCustomPatient(config: CustomGenerationConfig): GeneratedPatientData {
  const patient = generatePatient()
  const patientId = patient.id as string
  const { dateFrom, dateTo } = config

  const encounters = Array.from({ length: config.numEncounters }, () =>
    generateEncounter(patientId, dateFrom, dateTo),
  )

  const conditionMap = getConditionMap()
  const conditions = config.selectedConditions
    .map((code) => conditionMap.get(code))
    .filter((c): c is ConditionItem => !!c)
    .map((item) => generateCondition(patientId, item, dateFrom, dateTo))

  const observationMap = getObservationMap()
  const observations = config.selectedObservations
    .map((code) => observationMap.get(code))
    .filter((o): o is ObservationItem => !!o)
    .map((item) => generateObservation(patientId, item, dateFrom, dateTo))

  const medicationMap = getMedicationMap()
  const medPairs = config.selectedMedications
    .map((code) => medicationMap.get(code))
    .filter((m): m is MedicationItem => !!m)
    .map((item) => generateMedicationPair(patientId, item, dateFrom, dateTo))

  const allergyMap = getAllergyMap()
  const allergies = config.selectedAllergies
    .map((code) => allergyMap.get(code))
    .filter((a): a is AllergyItem => !!a)
    .map((item) => generateAllergyIntolerance(patientId, item, dateFrom, dateTo))

  return {
    patient,
    encounters,
    conditions,
    observations,
    medications: medPairs.map((p) => p.medication),
    medication_requests: medPairs.map((p) => p.request),
    allergies,
  }
}

export function generateBatch(config: BatchGenerationConfig): GeneratedPatientData[] {
  return Array.from({ length: config.numPatients }, () => generateCompletePatient(config))
}

export function downloadAsJson(data: GeneratedPatientData[], filename?: string): void {
  const name = filename ?? `tw_patients_${new Date().toISOString().replace(/[:.]/g, '-')}.json`
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  downloadBlob(blob, name)
}

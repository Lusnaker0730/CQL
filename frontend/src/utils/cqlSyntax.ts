import type { languages } from 'monaco-editor'

export const cqlLanguageConfiguration: languages.LanguageConfiguration = {
  comments: {
    lineComment: '//',
    blockComment: ['/*', '*/'],
  },
  brackets: [
    ['{', '}'],
    ['[', ']'],
    ['(', ')'],
  ],
  autoClosingPairs: [
    { open: '{', close: '}' },
    { open: '[', close: ']' },
    { open: '(', close: ')' },
    { open: '"', close: '"' },
    { open: "'", close: "'" },
  ],
  surroundingPairs: [
    { open: '{', close: '}' },
    { open: '[', close: ']' },
    { open: '(', close: ')' },
    { open: '"', close: '"' },
    { open: "'", close: "'" },
  ],
  folding: {
    markers: {
      start: /^\s*\/\/\s*#?region\b/,
      end: /^\s*\/\/\s*#?endregion\b/,
    },
  },
}

export const cqlTokensProvider: languages.IMonarchLanguage = {
  defaultToken: '',
  tokenPostfix: '.cql',

  keywords: [
    'library',
    'version',
    'using',
    'include',
    'called',
    'public',
    'private',
    'parameter',
    'default',
    'codesystem',
    'valueset',
    'code',
    'concept',
    'context',
    'define',
    'function',
    'returns',
    'external',
    'from',
    'where',
    'return',
    'all',
    'distinct',
    'sort',
    'by',
    'asc',
    'ascending',
    'desc',
    'descending',
    'is',
    'as',
    'cast',
    'between',
    'and',
    'or',
    'xor',
    'not',
    'if',
    'then',
    'else',
    'case',
    'when',
    'null',
    'true',
    'false',
    'exists',
    'in',
    'such',
    'that',
    'with',
    'without',
    'let',
    'union',
    'intersect',
    'except',
    'starts',
    'ends',
    'occurs',
    'same',
    'includes',
    'during',
    'included',
    'before',
    'after',
    'within',
    'meets',
    'overlaps',
    'collapse',
    'expand',
    'per',
    'properly',
    'day',
    'days',
    'week',
    'weeks',
    'month',
    'months',
    'year',
    'years',
    'hour',
    'hours',
    'minute',
    'minutes',
    'second',
    'seconds',
    'millisecond',
    'milliseconds',
    'time',
    'timezone',
    'date',
    'datetime',
    'interval',
    'contains',
    'display',
    'predecessor',
    'successor',
    'singleton',
    'point',
    'start',
    'end',
    'width',
    'difference',
    'duration',
    'flatten',
    'combine',
    'first',
    'last',
    'indexof',
    'skip',
    'take',
    'tail',
    'aggregate',
    'convert',
    'to',
  ],

  typeKeywords: [
    'Boolean',
    'Integer',
    'Decimal',
    'String',
    'Date',
    'DateTime',
    'Time',
    'Quantity',
    'Ratio',
    'Code',
    'Concept',
    'Interval',
    'List',
    'Tuple',
    'Choice',
    'Any',
  ],

  fhirResources: [
    'Patient',
    'Encounter',
    'Condition',
    'Observation',
    'Procedure',
    'MedicationRequest',
    'MedicationStatement',
    'MedicationAdministration',
    'DiagnosticReport',
    'ServiceRequest',
    'Immunization',
    'AllergyIntolerance',
    'CarePlan',
    'Goal',
    'Claim',
    'Coverage',
    'Organization',
    'Practitioner',
    'Location',
    'Device',
    'Specimen',
    'Bundle',
    'Composition',
    'DocumentReference',
    'Binary',
    'Medication',
    'ImagingStudy',
  ],

  operators: [
    '=',
    '>',
    '<',
    '!',
    '~',
    '?',
    ':',
    '==',
    '<=',
    '>=',
    '!=',
    '&&',
    '||',
    '++',
    '--',
    '+',
    '-',
    '*',
    '/',
    '&',
    '|',
    '^',
    '%',
    '<<',
    '>>',
    '>>>',
    '+=',
    '-=',
    '*=',
    '/=',
    '&=',
    '|=',
    '^=',
    '%=',
    '<<=',
    '>>=',
    '>>>=',
  ],

  symbols: /[=><!~?:&|+\-*/^%]+/,

  escapes: /\\(?:[abfnrtv\\"']|x[0-9A-Fa-f]{1,4}|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8})/,

  tokenizer: {
    root: [
      [
        /[a-zA-Z_]\w*/,
        {
          cases: {
            '@keywords': 'keyword',
            '@typeKeywords': 'type',
            '@fhirResources': 'type.identifier',
            '@default': 'identifier',
          },
        },
      ],

      { include: '@whitespace' },

      [/[{}()[\]]/, '@brackets'],

      [/[<>](?!@symbols)/, '@brackets'],

      [
        /@symbols/,
        {
          cases: {
            '@operators': 'operator',
            '@default': '',
          },
        },
      ],

      [/\d*\.\d+([eE][-+]?\d+)?/, 'number.float'],
      [/\d+/, 'number'],

      [/[;,.]/, 'delimiter'],

      [/"([^"\\]|\\.)*$/, 'string.invalid'],
      [/'([^'\\]|\\.)*$/, 'string.invalid'],
      [/"/, 'string', '@string_double'],
      [/'/, 'string', '@string_single'],
    ],

    whitespace: [
      [/[ \t\r\n]+/, 'white'],
      [/\/\*/, 'comment', '@comment'],
      [/\/\/.*$/, 'comment'],
    ],

    comment: [
      [/[^/*]+/, 'comment'],
      [/\/\*/, 'comment', '@push'],
      ['\\*/', 'comment', '@pop'],
      [/[/*]/, 'comment'],
    ],

    string_double: [
      [/[^\\"]+/, 'string'],
      [/@escapes/, 'string.escape'],
      [/\\./, 'string.escape.invalid'],
      [/"/, 'string', '@pop'],
    ],

    string_single: [
      [/[^\\']+/, 'string'],
      [/@escapes/, 'string.escape'],
      [/\\./, 'string.escape.invalid'],
      [/'/, 'string', '@pop'],
    ],
  },
}

export const cqlCompletionItems: languages.CompletionItem[] = [
  // Basic CQL constructs
  {
    label: 'library',
    kind: 14,
    insertText: 'library ${1:LibraryName} version \'${2:1.0.0}\'',
    insertTextRules: 4,
    documentation: 'Declares the library name and version',
  },
  {
    label: 'using',
    kind: 14,
    insertText: 'using ${1:FHIR} version \'${2:4.0.1}\'',
    insertTextRules: 4,
    documentation: 'Imports a data model',
  },
  {
    label: 'include',
    kind: 14,
    insertText: 'include ${1:LibraryName} version \'${2:1.0.0}\' called ${3:Alias}',
    insertTextRules: 4,
    documentation: 'Includes another CQL library',
  },
  {
    label: 'codesystem',
    kind: 14,
    insertText: 'codesystem "${1:Name}": \'${2:http://example.org/codesystem}\'',
    insertTextRules: 4,
    documentation: 'Defines a code system',
  },
  {
    label: 'valueset',
    kind: 14,
    insertText: 'valueset "${1:Name}": \'${2:http://example.org/valueset}\'',
    insertTextRules: 4,
    documentation: 'Defines a value set',
  },
  {
    label: 'code',
    kind: 14,
    insertText: 'code "${1:Name}": \'${2:code}\' from "${3:CodeSystem}" display \'${4:Display}\'',
    insertTextRules: 4,
    documentation: 'Defines a code',
  },
  {
    label: 'parameter',
    kind: 14,
    insertText: 'parameter "${1:Name}" ${2:Type} default ${3:value}',
    insertTextRules: 4,
    documentation: 'Defines a parameter',
  },
  {
    label: 'context',
    kind: 14,
    insertText: 'context ${1|Patient,Practitioner,Encounter|}',
    insertTextRules: 4,
    documentation: 'Sets the evaluation context',
  },
  {
    label: 'define',
    kind: 14,
    insertText: 'define "${1:ExpressionName}":\n  ${2:expression}',
    insertTextRules: 4,
    documentation: 'Defines an expression',
  },
  {
    label: 'define function',
    kind: 14,
    insertText: 'define function "${1:FunctionName}"(${2:params}):\n  ${3:expression}',
    insertTextRules: 4,
    documentation: 'Defines a function',
  },
  {
    label: 'retrieve',
    kind: 14,
    insertText: '[${1:ResourceType}: ${2:valueset}]',
    insertTextRules: 4,
    documentation: 'Retrieves FHIR resources',
  },
  {
    label: 'exists',
    kind: 14,
    insertText: 'exists (${1:expression})',
    insertTextRules: 4,
    documentation: 'Checks if a list has any elements',
  },
  {
    label: 'where',
    kind: 14,
    insertText: 'where ${1:condition}',
    insertTextRules: 4,
    documentation: 'Filters a list based on a condition',
  },
  {
    label: 'sort by',
    kind: 14,
    insertText: 'sort by ${1:field} ${2|asc,desc|}',
    insertTextRules: 4,
    documentation: 'Sorts a list',
  },
  {
    label: 'Measurement Period',
    kind: 14,
    insertText: 'parameter "Measurement Period" Interval<DateTime>\n  default Interval[@${1:2024}-01-01, @${1:2024}-12-31]',
    insertTextRules: 4,
    documentation: 'Standard measurement period parameter',
  },
  {
    label: 'Initial Population',
    kind: 14,
    insertText: 'define "Initial Population":\n  ${1:true}',
    insertTextRules: 4,
    documentation: 'Defines the initial population for a measure',
  },
  {
    label: 'Denominator',
    kind: 14,
    insertText: 'define "Denominator":\n  "Initial Population"',
    insertTextRules: 4,
    documentation: 'Defines the denominator for a measure',
  },
  {
    label: 'Numerator',
    kind: 14,
    insertText: 'define "Numerator":\n  ${1:expression}',
    insertTextRules: 4,
    documentation: 'Defines the numerator for a measure',
  },
  // Phase 5.4: Practical Code Snippets
  {
    label: 'Quality Measure Template',
    kind: 14,
    insertText: `library \${1:MeasureName} version '\${2:1.0.0}'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1' called FHIRHelpers

parameter "Measurement Period" Interval<DateTime>
  default Interval[@\${3:2024}-01-01, @\${3:2024}-12-31]

context Patient

define "Initial Population":
  \${4:true}

define "Denominator":
  "Initial Population"

define "Denominator Exclusions":
  \${5:false}

define "Numerator":
  \${6:true}`,
    insertTextRules: 4,
    documentation: 'Complete quality measure library template with all population groups',
  },
  {
    label: 'CDS Hook Library',
    kind: 14,
    insertText: `library \${1:CDSRuleName} version '\${2:1.0.0}'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1' called FHIRHelpers

context Patient

define "Applicable":
  \${3:true}

define "Recommendation":
  if "Applicable" then '\${4:Recommendation text}'
  else null

define "Indicator":
  if "Applicable" then '\${5|info,warning,critical|}'
  else null`,
    insertTextRules: 4,
    documentation: 'CDS Hooks library template with applicable/recommendation/indicator pattern',
  },
  {
    label: 'Age Check',
    kind: 14,
    insertText: 'AgeInYearsAt(start of "Measurement Period") >= ${1:18}',
    insertTextRules: 4,
    documentation: 'Check patient age at start of measurement period',
  },
  {
    label: 'Active Conditions',
    kind: 14,
    insertText: `define "Active Conditions":
  [Condition: \${1:"Condition ValueSet"}] C
    where C.clinicalStatus ~ ToConcept(Global."active")
      and C.verificationStatus ~ ToConcept(Global."confirmed")`,
    insertTextRules: 4,
    documentation: 'Query for active, confirmed conditions',
  },
  {
    label: 'Completed Encounters',
    kind: 14,
    insertText: `define "Qualifying Encounters":
  [Encounter: \${1:"Encounter Type ValueSet"}] E
    where E.status = 'finished'
      and E.period during "Measurement Period"`,
    insertTextRules: 4,
    documentation: 'Query for completed encounters during measurement period',
  },
  {
    label: 'Most Recent Observation',
    kind: 14,
    insertText: `define "Most Recent \${1:Lab}":
  Last(
    [\${2:Observation}: \${3:"Observation ValueSet"}] O
      where O.status in { 'final', 'amended', 'corrected' }
      sort by issued
  )`,
    insertTextRules: 4,
    documentation: 'Get the most recent observation of a given type',
  },
  {
    label: 'Active Medication',
    kind: 14,
    insertText: `define "Active Medications":
  [MedicationRequest: \${1:"Medication ValueSet"}] M
    where M.status = 'active'
      and M.intent = 'order'`,
    insertTextRules: 4,
    documentation: 'Query for active medication orders',
  },
  {
    label: 'Interval Operations',
    kind: 14,
    insertText: `define "Overlapping Period":
  Interval[\${1:start}, \${2:end}] overlaps \${3:"Measurement Period"}`,
    insertTextRules: 4,
    documentation: 'Interval overlap check',
  },
  {
    label: 'Stratifier',
    kind: 14,
    insertText: `define "Stratification":
  case
    when AgeInYearsAt(start of "Measurement Period") in Interval[18, 44] then '18-44'
    when AgeInYearsAt(start of "Measurement Period") in Interval[45, 64] then '45-64'
    when AgeInYearsAt(start of "Measurement Period") >= 65 then '65+'
    else null
  end`,
    insertTextRules: 4,
    documentation: 'Age-based stratifier with case expression',
  },
  {
    label: 'Hospitalization Function',
    kind: 14,
    insertText: `define function "Hospitalization"(Encounter Encounter):
  Encounter E
    let stayStart: start of E.period,
        stayEnd: end of E.period
    return Interval[stayStart, stayEnd]`,
    insertTextRules: 4,
    documentation: 'Hospitalization period function pattern',
  },
] as languages.CompletionItem[]

// CQL built-in function completions
export const cqlBuiltInFunctions: languages.CompletionItem[] = [
  { label: 'AgeInYears', kind: 1, insertText: 'AgeInYears()', documentation: 'Returns the age of the patient in years at the current date' },
  { label: 'AgeInYearsAt', kind: 1, insertText: 'AgeInYearsAt(${1:date})', insertTextRules: 4, documentation: 'Returns the age of the patient in years at a given date' },
  { label: 'AgeInMonths', kind: 1, insertText: 'AgeInMonths()', documentation: 'Returns the age of the patient in months' },
  { label: 'AgeInDays', kind: 1, insertText: 'AgeInDays()', documentation: 'Returns the age of the patient in days' },
  { label: 'Count', kind: 1, insertText: 'Count(${1:list})', insertTextRules: 4, documentation: 'Returns the number of elements in a list' },
  { label: 'Sum', kind: 1, insertText: 'Sum(${1:list})', insertTextRules: 4, documentation: 'Returns the sum of values in a list' },
  { label: 'Avg', kind: 1, insertText: 'Avg(${1:list})', insertTextRules: 4, documentation: 'Returns the average of values in a list' },
  { label: 'Min', kind: 1, insertText: 'Min(${1:list})', insertTextRules: 4, documentation: 'Returns the minimum value in a list' },
  { label: 'Max', kind: 1, insertText: 'Max(${1:list})', insertTextRules: 4, documentation: 'Returns the maximum value in a list' },
  { label: 'First', kind: 1, insertText: 'First(${1:list})', insertTextRules: 4, documentation: 'Returns the first element of a list' },
  { label: 'Last', kind: 1, insertText: 'Last(${1:list})', insertTextRules: 4, documentation: 'Returns the last element of a list' },
  { label: 'Today', kind: 1, insertText: 'Today()', documentation: 'Returns the current date' },
  { label: 'Now', kind: 1, insertText: 'Now()', documentation: 'Returns the current date and time' },
  { label: 'Coalesce', kind: 1, insertText: 'Coalesce(${1:a}, ${2:b})', insertTextRules: 4, documentation: 'Returns the first non-null argument' },
  { label: 'ToString', kind: 1, insertText: 'ToString(${1:value})', insertTextRules: 4, documentation: 'Converts a value to String' },
  { label: 'ToInteger', kind: 1, insertText: 'ToInteger(${1:value})', insertTextRules: 4, documentation: 'Converts a value to Integer' },
  { label: 'ToDecimal', kind: 1, insertText: 'ToDecimal(${1:value})', insertTextRules: 4, documentation: 'Converts a value to Decimal' },
  { label: 'ToDateTime', kind: 1, insertText: 'ToDateTime(${1:value})', insertTextRules: 4, documentation: 'Converts a value to DateTime' },
  { label: 'ToDate', kind: 1, insertText: 'ToDate(${1:value})', insertTextRules: 4, documentation: 'Converts a value to Date' },
  { label: 'ToConcept', kind: 1, insertText: 'ToConcept(${1:code})', insertTextRules: 4, documentation: 'Converts a Code to Concept' },
  { label: 'ToQuantity', kind: 1, insertText: 'ToQuantity(${1:value})', insertTextRules: 4, documentation: 'Converts a value to Quantity' },
  { label: 'Length', kind: 1, insertText: 'Length(${1:string})', insertTextRules: 4, documentation: 'Returns the length of a string' },
  { label: 'Upper', kind: 1, insertText: 'Upper(${1:string})', insertTextRules: 4, documentation: 'Converts string to uppercase' },
  { label: 'Lower', kind: 1, insertText: 'Lower(${1:string})', insertTextRules: 4, documentation: 'Converts string to lowercase' },
  { label: 'Flatten', kind: 1, insertText: 'Flatten(${1:list})', insertTextRules: 4, documentation: 'Flattens a list of lists' },
] as languages.CompletionItem[]

// TWCDI (Taiwan Core Data for Interoperability) snippet completions
export const twcdiCompletionItems: languages.CompletionItem[] = [
  {
    label: 'TWCDI: Library Header',
    kind: 14,
    insertText: `library \${1:TWCoreLibrary} version '\${2:1.0.0}'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1' called FHIRHelpers

// Taiwan Core code systems
codesystem "LOINC": 'http://loinc.org'
codesystem "ICD-10-CM-TW": 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-cm-2023-tw'
codesystem "NHI Medication": 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medication-nhi-tw'

context Patient`,
    insertTextRules: 4,
    documentation: 'Complete TW Core CQL library header with Taiwan code systems',
  },
  {
    label: 'TWCDI: Blood Pressure',
    kind: 14,
    insertText: `codesystem "LOINC": 'http://loinc.org'
code "BP Panel": '85354-9' from "LOINC" display 'Blood Pressure Panel'

define "Blood Pressure Readings":
  [Observation: "BP Panel"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Most Recent BP":
  Last("Blood Pressure Readings")`,
    insertTextRules: 4,
    documentation: 'TW Core blood pressure observation query',
  },
  {
    label: 'TWCDI: Body Weight & Height',
    kind: 14,
    insertText: `code "Weight Code": '29463-7' from "LOINC" display 'Body Weight'
code "Height Code": '8302-2' from "LOINC" display 'Body Height'

define "Body Weight":
  [Observation: "Weight Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Body Height":
  [Observation: "Height Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc`,
    insertTextRules: 4,
    documentation: 'TW Core body weight and height queries',
  },
  {
    label: 'TWCDI: BMI',
    kind: 14,
    insertText: `code "BMI Code": '39156-5' from "LOINC" display 'BMI'

define "BMI Results":
  [Observation: "BMI Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Most Recent BMI":
  Last("BMI Results")`,
    insertTextRules: 4,
    documentation: 'TW Core BMI observation query',
  },
  {
    label: 'TWCDI: Heart Rate',
    kind: 14,
    insertText: `code "HR Code": '8867-4' from "LOINC" display 'Heart Rate'

define "Heart Rate":
  [Observation: "HR Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc`,
    insertTextRules: 4,
    documentation: 'TW Core heart rate observation query',
  },
  {
    label: 'TWCDI: Temperature',
    kind: 14,
    insertText: `code "Temp Code": '8310-5' from "LOINC" display 'Body Temperature'

define "Body Temperature":
  [Observation: "Temp Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc`,
    insertTextRules: 4,
    documentation: 'TW Core body temperature query',
  },
  {
    label: 'TWCDI: SpO2',
    kind: 14,
    insertText: `code "SpO2 Code": '2708-6' from "LOINC" display 'Oxygen Saturation'

define "SpO2":
  [Observation: "SpO2 Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc`,
    insertTextRules: 4,
    documentation: 'TW Core oxygen saturation (SpO2) query',
  },
  {
    label: 'TWCDI: HbA1c',
    kind: 14,
    insertText: `code "HbA1c Code": '4548-4' from "LOINC" display 'Hemoglobin A1c'

define "HbA1c Results":
  [Observation: "HbA1c Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Most Recent HbA1c":
  Last("HbA1c Results")`,
    insertTextRules: 4,
    documentation: 'TW Core HbA1c lab result query',
  },
  {
    label: 'TWCDI: Glucose',
    kind: 14,
    insertText: `code "Glucose Code": '2345-7' from "LOINC" display 'Glucose'

define "Glucose Results":
  [Observation: "Glucose Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc`,
    insertTextRules: 4,
    documentation: 'TW Core glucose lab result query',
  },
  {
    label: 'TWCDI: eGFR',
    kind: 14,
    insertText: `code "eGFR Code": '48642-3' from "LOINC" display 'eGFR'

define "eGFR Results":
  [Observation: "eGFR Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc`,
    insertTextRules: 4,
    documentation: 'TW Core eGFR (kidney function) query',
  },
  {
    label: 'TWCDI: Lipid Panel',
    kind: 14,
    insertText: `code "Cholesterol Code": '2093-3' from "LOINC" display 'Total Cholesterol'
code "LDL Code": '2089-1' from "LOINC" display 'LDL Cholesterol'
code "HDL Code": '2085-9' from "LOINC" display 'HDL Cholesterol'
code "Triglycerides Code": '2571-8' from "LOINC" display 'Triglycerides'

define "Total Cholesterol":
  [Observation: "Cholesterol Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "LDL Cholesterol":
  [Observation: "LDL Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "HDL Cholesterol":
  [Observation: "HDL Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Triglycerides":
  [Observation: "Triglycerides Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc`,
    insertTextRules: 4,
    documentation: 'TW Core lipid panel queries (cholesterol, LDL, HDL, triglycerides)',
  },
  {
    label: 'TWCDI: CBC',
    kind: 14,
    insertText: `code "WBC Code": '6690-2' from "LOINC" display 'WBC'
code "Hemoglobin Code": '718-7' from "LOINC" display 'Hemoglobin'
code "Platelets Code": '777-3' from "LOINC" display 'Platelets'

define "WBC":
  [Observation: "WBC Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Hemoglobin":
  [Observation: "Hemoglobin Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Platelets":
  [Observation: "Platelets Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc`,
    insertTextRules: 4,
    documentation: 'TW Core CBC (Complete Blood Count) queries',
  },
  {
    label: 'TWCDI: Active Medications',
    kind: 14,
    insertText: `define "Active Medications":
  [MedicationRequest] M
    where M.status = 'active'
      and M.intent = 'order'
    sort by authoredOn desc

define "Medication Statements":
  [MedicationStatement] M
    where M.status = 'active'`,
    insertTextRules: 4,
    documentation: 'TW Core active medication queries',
  },
  {
    label: 'TWCDI: Allergies',
    kind: 14,
    insertText: `define "Active Allergies":
  [AllergyIntolerance] A
    where A.clinicalStatus.coding[0].code = 'active'

define "High Risk Allergies":
  [AllergyIntolerance] A
    where A.criticality = 'high'
      and A.clinicalStatus.coding[0].code = 'active'`,
    insertTextRules: 4,
    documentation: 'TW Core allergy and intolerance queries',
  },
  {
    label: 'TWCDI: Conditions',
    kind: 14,
    insertText: `define "Active Conditions":
  [Condition] C
    where C.clinicalStatus.coding[0].code = 'active'
    sort by recordedDate desc

define "Confirmed Diagnoses":
  [Condition] C
    where C.verificationStatus.coding[0].code = 'confirmed'
    sort by recordedDate desc`,
    insertTextRules: 4,
    documentation: 'TW Core condition and diagnosis queries',
  },
  {
    label: 'TWCDI: Encounters',
    kind: 14,
    insertText: `define "Recent Encounters":
  [Encounter] E
    where E.status = 'finished'
    sort by period.start desc

define "Inpatient Encounters":
  [Encounter] E
    where E.class.code = 'IMP'
    sort by period.start desc`,
    insertTextRules: 4,
    documentation: 'TW Core encounter queries',
  },
  {
    label: 'TWCDI: Procedures',
    kind: 14,
    insertText: `define "Completed Procedures":
  [Procedure] P
    where P.status = 'completed'
    sort by performed desc`,
    insertTextRules: 4,
    documentation: 'TW Core procedure queries',
  },
  {
    label: 'TWCDI: Immunizations',
    kind: 14,
    insertText: `define "Completed Immunizations":
  [Immunization] I
    where I.status = 'completed'
    sort by occurrence desc

define "Immunization Count":
  Count("Completed Immunizations")`,
    insertTextRules: 4,
    documentation: 'TW Core immunization queries',
  },
] as languages.CompletionItem[]

// TW Core code system declaration snippets
export const twCodeSystemSnippets: languages.CompletionItem[] = [
  {
    label: 'TW codesystem: LOINC',
    kind: 14,
    insertText: 'codesystem "LOINC": \'http://loinc.org\'',
    documentation: 'LOINC code system (used for labs & vitals in TW Core)',
  },
  {
    label: 'TW codesystem: ICD-10-CM-TW',
    kind: 14,
    insertText: 'codesystem "ICD-10-CM-TW": \'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-cm-2023-tw\'',
    documentation: 'Taiwan ICD-10-CM code system for diagnoses',
  },
  {
    label: 'TW codesystem: ICD-10-PCS-TW',
    kind: 14,
    insertText: 'codesystem "ICD-10-PCS-TW": \'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-pcs-2023-tw\'',
    documentation: 'Taiwan ICD-10-PCS code system for procedures',
  },
  {
    label: 'TW codesystem: NHI Medication',
    kind: 14,
    insertText: 'codesystem "NHI Medication": \'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medication-nhi-tw\'',
    documentation: 'Taiwan NHI medication code system',
  },
  {
    label: 'TW codesystem: NHI Procedure',
    kind: 14,
    insertText: 'codesystem "NHI Procedure": \'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medical-service-payment-tw\'',
    documentation: 'Taiwan NHI medical service payment code system',
  },
] as languages.CompletionItem[]

// FHIR R4 resource property map for dot-completion
export const fhirResourceProperties: Record<string, Array<{ name: string; type: string; doc: string }>> = {
  Patient: [
    { name: 'id', type: 'string', doc: 'Logical id of this resource' },
    { name: 'name', type: 'List<HumanName>', doc: 'A name associated with the patient' },
    { name: 'gender', type: 'code', doc: 'male | female | other | unknown' },
    { name: 'birthDate', type: 'date', doc: 'Date of birth' },
    { name: 'active', type: 'boolean', doc: 'Whether the record is in active use' },
    { name: 'address', type: 'List<Address>', doc: 'Addresses for the individual' },
    { name: 'telecom', type: 'List<ContactPoint>', doc: 'Contact details' },
    { name: 'identifier', type: 'List<Identifier>', doc: 'An identifier for this patient' },
    { name: 'deceased', type: 'boolean|dateTime', doc: 'Indicates if the individual is deceased' },
    { name: 'maritalStatus', type: 'CodeableConcept', doc: 'Marital (civil) status' },
  ],
  Encounter: [
    { name: 'id', type: 'string', doc: 'Logical id' },
    { name: 'status', type: 'code', doc: 'planned | arrived | triaged | in-progress | finished | cancelled' },
    { name: 'class', type: 'Coding', doc: 'Classification of encounter' },
    { name: 'type', type: 'List<CodeableConcept>', doc: 'Specific type of encounter' },
    { name: 'period', type: 'Period', doc: 'The start and end time of the encounter' },
    { name: 'subject', type: 'Reference(Patient)', doc: 'The patient present at the encounter' },
    { name: 'reasonCode', type: 'List<CodeableConcept>', doc: 'Coded reason the encounter takes place' },
    { name: 'diagnosis', type: 'List<BackboneElement>', doc: 'The list of diagnosis relevant to this encounter' },
    { name: 'hospitalization', type: 'BackboneElement', doc: 'Details about the admission' },
    { name: 'location', type: 'List<BackboneElement>', doc: 'List of locations where the patient has been' },
  ],
  Condition: [
    { name: 'id', type: 'string', doc: 'Logical id' },
    { name: 'clinicalStatus', type: 'CodeableConcept', doc: 'active | recurrence | relapse | inactive | remission | resolved' },
    { name: 'verificationStatus', type: 'CodeableConcept', doc: 'confirmed | unconfirmed | provisional | differential | refuted' },
    { name: 'category', type: 'List<CodeableConcept>', doc: 'problem-list-item | encounter-diagnosis' },
    { name: 'code', type: 'CodeableConcept', doc: 'Identification of the condition' },
    { name: 'subject', type: 'Reference(Patient)', doc: 'Who has the condition' },
    { name: 'onset', type: 'dateTime|Age|Period|Range|string', doc: 'Estimated or actual date/age' },
    { name: 'abatement', type: 'dateTime|Age|Period|Range|string', doc: 'When in resolution/remission' },
    { name: 'recordedDate', type: 'dateTime', doc: 'Date record was first recorded' },
    { name: 'severity', type: 'CodeableConcept', doc: 'Subjective severity of condition' },
  ],
  Observation: [
    { name: 'id', type: 'string', doc: 'Logical id' },
    { name: 'status', type: 'code', doc: 'registered | preliminary | final | amended | corrected' },
    { name: 'category', type: 'List<CodeableConcept>', doc: 'Classification of type of observation' },
    { name: 'code', type: 'CodeableConcept', doc: 'Type of observation (code / type)' },
    { name: 'subject', type: 'Reference(Patient)', doc: 'Who the observation is about' },
    { name: 'effective', type: 'dateTime|Period|Timing|instant', doc: 'Clinically relevant time for observation' },
    { name: 'issued', type: 'instant', doc: 'Date/Time observation was made available' },
    { name: 'value', type: 'Quantity|CodeableConcept|string|boolean|integer|Range|Ratio', doc: 'Actual result' },
    { name: 'interpretation', type: 'List<CodeableConcept>', doc: 'High, low, normal, etc.' },
    { name: 'component', type: 'List<BackboneElement>', doc: 'Component observations (e.g., systolic/diastolic)' },
  ],
  Procedure: [
    { name: 'id', type: 'string', doc: 'Logical id' },
    { name: 'status', type: 'code', doc: 'preparation | in-progress | completed | entered-in-error' },
    { name: 'code', type: 'CodeableConcept', doc: 'Identification of the procedure' },
    { name: 'subject', type: 'Reference(Patient)', doc: 'Who the procedure was performed on' },
    { name: 'performed', type: 'dateTime|Period|string|Age|Range', doc: 'When the procedure was performed' },
    { name: 'category', type: 'CodeableConcept', doc: 'Classification of the procedure' },
    { name: 'reasonCode', type: 'List<CodeableConcept>', doc: 'Coded reason procedure performed' },
  ],
  MedicationRequest: [
    { name: 'id', type: 'string', doc: 'Logical id' },
    { name: 'status', type: 'code', doc: 'active | on-hold | cancelled | completed | stopped | draft' },
    { name: 'intent', type: 'code', doc: 'proposal | plan | order | instance-order | option' },
    { name: 'medication', type: 'CodeableConcept|Reference(Medication)', doc: 'Medication to be taken' },
    { name: 'subject', type: 'Reference(Patient)', doc: 'Who the medication request is for' },
    { name: 'authoredOn', type: 'dateTime', doc: 'When request was initially authored' },
    { name: 'dosageInstruction', type: 'List<Dosage>', doc: 'How the medication should be taken' },
    { name: 'reasonCode', type: 'List<CodeableConcept>', doc: 'Reason or indication for ordering' },
  ],
  DiagnosticReport: [
    { name: 'id', type: 'string', doc: 'Logical id' },
    { name: 'status', type: 'code', doc: 'registered | partial | preliminary | final' },
    { name: 'code', type: 'CodeableConcept', doc: 'Name/Code for this diagnostic report' },
    { name: 'subject', type: 'Reference(Patient)', doc: 'The subject of the report' },
    { name: 'effective', type: 'dateTime|Period', doc: 'Clinically relevant time/period for report' },
    { name: 'issued', type: 'instant', doc: 'DateTime this version was made' },
    { name: 'result', type: 'List<Reference(Observation)>', doc: 'Observations' },
    { name: 'conclusion', type: 'string', doc: 'Clinical conclusion of test results' },
  ],
  Immunization: [
    { name: 'id', type: 'string', doc: 'Logical id' },
    { name: 'status', type: 'code', doc: 'completed | entered-in-error | not-done' },
    { name: 'vaccineCode', type: 'CodeableConcept', doc: 'Vaccine product administered' },
    { name: 'patient', type: 'Reference(Patient)', doc: 'Who was immunized' },
    { name: 'occurrence', type: 'dateTime|string', doc: 'Vaccine administration date' },
    { name: 'primarySource', type: 'boolean', doc: 'Indicates context the data was recorded in' },
  ],
  AllergyIntolerance: [
    { name: 'id', type: 'string', doc: 'Logical id' },
    { name: 'clinicalStatus', type: 'CodeableConcept', doc: 'active | inactive | resolved' },
    { name: 'verificationStatus', type: 'CodeableConcept', doc: 'unconfirmed | confirmed | refuted' },
    { name: 'type', type: 'code', doc: 'allergy | intolerance' },
    { name: 'category', type: 'List<code>', doc: 'food | medication | environment | biologic' },
    { name: 'code', type: 'CodeableConcept', doc: 'Code that identifies the allergy or intolerance' },
    { name: 'patient', type: 'Reference(Patient)', doc: 'Who the sensitivity is for' },
    { name: 'criticality', type: 'code', doc: 'low | high | unable-to-assess' },
  ],
  Medication: [
    { name: 'id', type: 'string', doc: 'Logical id' },
    { name: 'code', type: 'CodeableConcept', doc: 'Codes that identify this medication' },
    { name: 'status', type: 'code', doc: 'active | inactive | entered-in-error' },
    { name: 'manufacturer', type: 'Reference(Organization)', doc: 'Manufacturer of the item' },
    { name: 'form', type: 'CodeableConcept', doc: 'Dose form (tablet, capsule, etc.)' },
    { name: 'amount', type: 'Ratio', doc: 'Amount of drug in package' },
    { name: 'ingredient', type: 'List<BackboneElement>', doc: 'Active or inactive ingredient' },
  ],
}

export interface LibraryInfo {
  name: string
  version: string
  expressions?: string[]
  functions?: string[]
}

export function provideCqlCompletions(
  monaco: typeof import('monaco-editor'),
  model: import('monaco-editor').editor.ITextModel,
  position: import('monaco-editor').Position,
  libraries: LibraryInfo[] = [],
) {
  const word = model.getWordUntilPosition(position)
  const range = {
    startLineNumber: position.lineNumber,
    endLineNumber: position.lineNumber,
    startColumn: word.startColumn,
    endColumn: word.endColumn,
  }

  const lineContent = model.getLineContent(position.lineNumber)
  const textBeforeCursor = lineContent.substring(0, position.column - 1)

  // After dot: provide FHIR property completions
  const dotMatch = textBeforeCursor.match(/\[(\w+)[^\]]*\]\s*\w*\s*$/) ||
                   textBeforeCursor.match(/(\w+)\.\s*$/)
  if (dotMatch) {
    const resourceType = dotMatch[1]
    const props = fhirResourceProperties[resourceType]
    if (props) {
      return {
        suggestions: props.map((prop) => ({
          label: prop.name,
          kind: monaco.languages.CompletionItemKind.Property,
          insertText: prop.name,
          documentation: `${prop.type} - ${prop.doc}`,
          range,
        })),
      }
    }
  }

  // After 'include ': suggest saved libraries
  if (textBeforeCursor.match(/include\s+$/)) {
    const libSuggestions = libraries.map((lib) => ({
      label: lib.name,
      kind: monaco.languages.CompletionItemKind.Module,
      insertText: `${lib.name} version '${lib.version}' called \${1:${lib.name}}`,
      insertTextRules: 4 as const,
      documentation: `Library ${lib.name} v${lib.version}`,
      range,
    }))
    if (libSuggestions.length > 0) {
      return { suggestions: libSuggestions }
    }
  }

  // Default: all snippets + built-in functions + TWCDI completions
  const suggestions = [
    ...cqlCompletionItems.map((item) => ({ ...item, range })),
    ...cqlBuiltInFunctions.map((item) => ({ ...item, range })),
    ...twcdiCompletionItems.map((item) => ({ ...item, range })),
    ...twCodeSystemSnippets.map((item) => ({ ...item, range })),
  ]

  return { suggestions }
}

export function registerCqlLanguage(
  monaco: typeof import('monaco-editor'),
  libraries: LibraryInfo[] = [],
) {
  monaco.languages.register({ id: 'cql' })

  monaco.languages.setLanguageConfiguration('cql', cqlLanguageConfiguration)

  monaco.languages.setMonarchTokensProvider('cql', cqlTokensProvider)

  monaco.languages.registerCompletionItemProvider('cql', {
    triggerCharacters: ['.', '"'],
    provideCompletionItems: (model, position) => {
      return provideCqlCompletions(monaco, model, position, libraries)
    },
  })

  monaco.editor.defineTheme('cql-theme', {
    base: 'vs',
    inherit: true,
    rules: [
      { token: 'keyword', foreground: '0D7377', fontStyle: 'bold' },
      { token: 'type', foreground: '1B3A5C' },
      { token: 'type.identifier', foreground: '2D5F8A' },
      { token: 'string', foreground: 'A31515' },
      { token: 'number', foreground: '095052' },
      { token: 'comment', foreground: '546E7A', fontStyle: 'italic' },
      { token: 'operator', foreground: '1A2B3C' },
      { token: 'identifier', foreground: '1A2B3C' },
    ],
    colors: {
      'editor.background': '#FAFCFD',
      'editor.foreground': '#1A2B3C',
      'editorLineNumber.foreground': '#90A4AE',
      'editorLineNumber.activeForeground': '#0D7377',
      'editorCursor.foreground': '#0D7377',
      'editor.selectionBackground': '#0D737730',
      'editor.lineHighlightBackground': '#0D737708',
      'editorBracketMatch.background': '#0D737720',
      'editorBracketMatch.border': '#0D737740',
    },
  })
}

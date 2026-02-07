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

  // Default: all snippets + built-in functions
  const suggestions = [
    ...cqlCompletionItems.map((item) => ({ ...item, range })),
    ...cqlBuiltInFunctions.map((item) => ({ ...item, range })),
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

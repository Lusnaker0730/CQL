import { Box, Typography, Paper, Accordion, AccordionSummary, AccordionDetails, Alert, Grid } from '@mui/material'
import { ExpandMore as ExpandMoreIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import CodeBlock from './CodeBlock'
import { CARD_RADIUS } from '../../constants/layout'

const FHIRPATH_TRAVERSAL_CODE = `// FHIRPath navigation in CQL
// Dot notation traverses FHIR resource structure

// Simple path traversal
define "Patient Family Name":
  Patient.name[0].family

define "Patient Given Names":
  Patient.name[0].given

// Nested traversal
define "Observation Code Display":
  [Observation] O
    return O.code.coding[0].display

// Reference navigation
define "Encounter Location":
  [Encounter] E
    return E.location[0].location.display`

const FHIRPATH_WHERE_CODE = `// .where() — filter collections
define "Active Conditions":
  [Condition] C
    where C.clinicalStatus.coding.where(
      code = 'active'
    ).exists()

// Chaining .where() with other functions
define "High Priority Alerts":
  [Flag] F
    where F.code.coding.where(
      system = 'http://terminology.hl7.org/CodeSystem/flag-category'
        and code = 'clinical'
    ).exists()
      and F.status = 'active'`

const FHIRPATH_FUNCTIONS_CODE = `// .exists() — true if collection is non-empty
define "Has Any Allergies":
  [AllergyIntolerance].exists()

// .empty() — true if collection is empty
define "No Known Allergies":
  [AllergyIntolerance].empty()

// .count() — number of elements
define "Condition Count":
  [Condition].count()

// .first(), .last(), .single()
define "Most Recent Encounter":
  Last([Encounter] E sort by period.start)

// .ofType() — filter by FHIR type (polymorphic navigation)
define "Quantity Values Only":
  [Observation] O
    where O.value.ofType(Quantity).exists()

// .select() — project specific fields
define "All Diagnosis Codes":
  [Condition] C
    return C.code.coding.select(code)`

const FHIRPATH_RESOLVE_CODE = `// .resolve() — follow FHIR references
// Resolve a reference to get the actual resource

define "Encounter Practitioner":
  [Encounter] E
    return E.participant[0].individual.resolve() as Practitioner

define "Medication From Request":
  [MedicationRequest] MR
    where MR.medication.reference is not null
    return MR.medication.reference.resolve() as Medication`

const FHIRPATH_CLINICAL_CODE = `// Clinical example: navigating complex FHIR resources
library FhirPathDemo version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

context Patient

// Navigate Observation components (e.g., Blood Pressure Panel)
define "Blood Pressure Readings":
  [Observation] O
    where O.code.coding.where(code = '85354-9').exists()
    return Tuple {
      date: O.effective,
      systolic: (O.component.where(
        code.coding.where(code = '8480-6').exists()
      ).first().value as Quantity).value,
      diastolic: (O.component.where(
        code.coding.where(code = '8462-4').exists()
      ).first().value as Quantity).value
    }

// Navigate nested CodeableConcept
define "Active Medication Names":
  [MedicationRequest] MR
    where MR.status = 'active'
    return MR.medication.coding.where(
      system = 'http://www.nhi.gov.tw/medication'
    ).first().display`

const FHIRPATH_VS_CQL_CODE = `// Key differences: FHIRPath expressions vs CQL queries

// FHIRPath-style (implicit iteration):
//   Patient.name.given
//   — iterates all names, flattens all given names

// CQL query-style (explicit iteration):
define "All Given Names":
  flatten(
    Patient.name N return N.given
  )

// FHIRPath .where() vs CQL where clause:
// Both filter, but CQL queries are more powerful
// with aliases, multi-source, return projections

// CQL adds: aggregate, let, sort, with/without
define "Complex Query Example":
  [Observation] O
    let value: (O.value as Quantity).value
    where O.code ~ "HbA1c"
      and value is not null
    return Tuple { date: O.effective, result: value }
    sort by date desc`

const ELM_INTRO_CODE = `// CQL source
define "Adult Patients":
  AgeInYears() >= 18`

const ELM_JSON_CODE = `// Corresponding ELM (JSON) output
{
  "library": {
    "identifier": { "id": "Example", "version": "1.0.0" },
    "statements": {
      "def": [{
        "name": "Adult Patients",
        "expression": {
          "type": "GreaterOrEqual",
          "operand": [{
            "type": "CalculateAge",
            "operand": {
              "type": "Property",
              "path": "birthDate",
              "source": { "type": "ExpressionRef", "name": "Patient" }
            },
            "precision": "Year"
          }, {
            "type": "Literal",
            "valueType": "{urn:hl7-org:elm-types:r1}Integer",
            "value": "18"
          }]
        }
      }]
    }
  }
}`

const ELM_NODES_CODE = `// Key ELM node types you'll encounter:

// Library — top-level container
//   Contains: usings, includes, parameters, codeSystems,
//             valueSets, statements

// ExpressionDef — a define statement
//   { "name": "My Define", "expression": { ... } }

// Retrieve — fetching FHIR data: [Condition]
//   { "type": "Retrieve", "dataType": "Condition" }

// Query — CQL query with where/return/sort
//   { "type": "Query", "source": [...],
//     "where": {...}, "return": {...} }

// FunctionRef — calling a function
//   { "type": "FunctionRef", "name": "AgeInYears" }

// Property — accessing a field
//   { "type": "Property", "path": "value", "source": {...} }`

const ELM_ERROR_CODE = `// Reading ELM errors for debugging

// Error example: type mismatch
// CQL:  define "Bad": [Observation].value > 5
// ELM error:
// {
//   "severity": "error",
//   "message": "Cannot invoke member 'value' on type
//              'List<FHIR.Observation>'",
//   "errorType": "semantic"
// }

// Fix: use a query alias
// define "Fixed": [Observation] O
//   where (O.value as Quantity).value > 5

// Error example: unresolved identifier
// { "severity": "error",
//   "message": "Could not resolve identifier 'BP' in
//              the current library." }
// Fix: check that 'BP' is defined before use`

export default function FhirPathElmGuide() {
  const { t } = useTranslation('landing')

  return (
    <Box>
      <Typography
        variant="h4"
        gutterBottom
        sx={{
          fontWeight: 700,
          color: "secondary.main"
        }}>
        {t('learn.fhirpathElm.title')}
      </Typography>
      <Typography
        variant="body1"
        sx={{
          color: "text.secondary",
          mb: 3
        }}>
        {t('learn.fhirpathElm.subtitle')}
      </Typography>
      {/* Section 1: FHIRPath in CQL */}
      <Accordion defaultExpanded sx={{ mb: 2, borderRadius: '12px !important', '&:before': { display: 'none' }, border: '1px solid', borderColor: 'divider' }} elevation={0}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography
            variant="h6"
            sx={{
              fontWeight: 700,
              color: "primary.main"
            }}>
            {t('learn.fhirpathElm.fhirpath.title')}
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Typography
            variant="body2"
            sx={{
              color: "text.secondary",
              mb: 2,
              lineHeight: 1.7
            }}>
            {t('learn.fhirpathElm.fhirpath.description')}
          </Typography>

          <Grid container spacing={2}>
            <Grid size={12}>
              <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: CARD_RADIUS }}>
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 600,
                    mb: 1
                  }}>
                  {t('learn.fhirpathElm.fhirpath.traversal')}
                </Typography>
                <CodeBlock code={FHIRPATH_TRAVERSAL_CODE} maxHeight={300} />
              </Paper>
            </Grid>
            <Grid size={12}>
              <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: CARD_RADIUS }}>
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 600,
                    mb: 1
                  }}>
                  {t('learn.fhirpathElm.fhirpath.whereFiltering')}
                </Typography>
                <CodeBlock code={FHIRPATH_WHERE_CODE} maxHeight={300} />
              </Paper>
            </Grid>
            <Grid size={12}>
              <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: CARD_RADIUS }}>
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 600,
                    mb: 1
                  }}>
                  {t('learn.fhirpathElm.fhirpath.collectionFunctions')}
                </Typography>
                <CodeBlock code={FHIRPATH_FUNCTIONS_CODE} maxHeight={400} />
              </Paper>
            </Grid>
            <Grid size={12}>
              <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: CARD_RADIUS }}>
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 600,
                    mb: 1
                  }}>
                  {t('learn.fhirpathElm.fhirpath.resolveReferences')}
                </Typography>
                <CodeBlock code={FHIRPATH_RESOLVE_CODE} maxHeight={260} />
              </Paper>
            </Grid>
            <Grid size={12}>
              <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: CARD_RADIUS }}>
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 600,
                    mb: 1
                  }}>
                  {t('learn.fhirpathElm.fhirpath.cqlVsFhirpath')}
                </Typography>
                <CodeBlock code={FHIRPATH_VS_CQL_CODE} maxHeight={360} />
              </Paper>
            </Grid>
            <Grid size={12}>
              <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: CARD_RADIUS }}>
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 600,
                    mb: 1
                  }}>
                  {t('learn.fhirpathElm.fhirpath.clinicalExample')}
                </Typography>
                <CodeBlock code={FHIRPATH_CLINICAL_CODE} maxHeight={500} />
              </Paper>
            </Grid>
          </Grid>

          <Alert severity="info" sx={{ mt: 2 }}>
            {t('learn.fhirpathElm.fhirpath.keyPoints')}
          </Alert>
        </AccordionDetails>
      </Accordion>
      {/* Section 2: Understanding ELM */}
      <Accordion defaultExpanded sx={{ mb: 2, borderRadius: '12px !important', '&:before': { display: 'none' }, border: '1px solid', borderColor: 'divider' }} elevation={0}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography
            variant="h6"
            sx={{
              fontWeight: 700,
              color: "primary.main"
            }}>
            {t('learn.fhirpathElm.elm.title')}
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Typography
            variant="body2"
            sx={{
              color: "text.secondary",
              mb: 2,
              lineHeight: 1.7
            }}>
            {t('learn.fhirpathElm.elm.description')}
          </Typography>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: CARD_RADIUS }}>
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 600,
                    mb: 1
                  }}>
                  {t('learn.fhirpathElm.elm.cqlSource')}
                </Typography>
                <CodeBlock code={ELM_INTRO_CODE} maxHeight={120} />
              </Paper>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: CARD_RADIUS }}>
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 600,
                    mb: 1
                  }}>
                  {t('learn.fhirpathElm.elm.elmOutput')}
                </Typography>
                <CodeBlock code={ELM_JSON_CODE} maxHeight={400} />
              </Paper>
            </Grid>
            <Grid size={12}>
              <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: CARD_RADIUS }}>
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 600,
                    mb: 1
                  }}>
                  {t('learn.fhirpathElm.elm.keyNodeTypes')}
                </Typography>
                <CodeBlock code={ELM_NODES_CODE} maxHeight={360} />
              </Paper>
            </Grid>
            <Grid size={12}>
              <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: CARD_RADIUS }}>
                <Typography
                  variant="subtitle2"
                  sx={{
                    fontWeight: 600,
                    mb: 1
                  }}>
                  {t('learn.fhirpathElm.elm.debuggingErrors')}
                </Typography>
                <CodeBlock code={ELM_ERROR_CODE} maxHeight={400} />
              </Paper>
            </Grid>
          </Grid>

          <Alert severity="info" sx={{ mt: 2 }}>
            {t('learn.fhirpathElm.elm.keyPoints')}
          </Alert>
        </AccordionDetails>
      </Accordion>
    </Box>
  );
}

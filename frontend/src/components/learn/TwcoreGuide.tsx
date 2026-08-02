import { Box, Typography, Paper, Grid, Chip, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material'
import { useTranslation } from 'react-i18next'
import CodeBlock from './CodeBlock'
import { CQL_EXAMPLE_DIABETES } from '../../constants/cqlExamples'
import { CARD_RADIUS, INNER_RADIUS } from '../../constants/layout'

const PROFILES = [
  { resource: 'Patient', profile: 'Patient-twcore', descKey: 'patientDesc' },
  { resource: 'Condition', profile: 'Condition-twcore', descKey: 'conditionDesc' },
  { resource: 'Observation', profile: 'Observation-laboratoryResult-twcore', descKey: 'observationDesc' },
  { resource: 'MedicationRequest', profile: 'MedicationRequest-twcore', descKey: 'medicationDesc' },
  { resource: 'Encounter', profile: 'Encounter-twcore', descKey: 'encounterDesc' },
  { resource: 'Procedure', profile: 'Procedure-twcore', descKey: 'procedureDesc' },
] as const

const CODE_SYSTEMS = [
  { key: 'icd10cm', url: 'https://twcore.mohw.gov.tw/.../icd-10-cm-2023-tw' },
  { key: 'icd10pcs', url: 'https://twcore.mohw.gov.tw/.../icd-10-pcs-2023-tw' },
  { key: 'nhiMed', url: 'https://twcore.mohw.gov.tw/.../medication-nhi-tw' },
  { key: 'nhiProc', url: 'https://twcore.mohw.gov.tw/.../medical-service-payment-tw' },
  { key: 'dept', url: 'https://twcore.mohw.gov.tw/.../department-tw' },
] as const

export default function TwcoreGuide() {
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
        {t('learn.twcore.title')}
      </Typography>
      <Grid container spacing={3} sx={{ mt: 1 }}>
        {/* Overview */}
        <Grid size={12}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: CARD_RADIUS, border: '1px solid', borderColor: 'divider' }}>
            <Typography
              variant="h6"
              gutterBottom
              sx={{
                fontWeight: 700,
                color: "primary.main"
              }}>
              {t('learn.twcore.overview.title')}
            </Typography>
            <Typography
              variant="body1"
              sx={{
                color: "text.secondary",
                lineHeight: 1.8
              }}>
              {t('learn.twcore.overview.content')}
            </Typography>
          </Paper>
        </Grid>

        {/* Profiles Table */}
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: CARD_RADIUS, border: '1px solid', borderColor: 'divider', height: '100%' }}>
            <Typography
              variant="h6"
              gutterBottom
              sx={{
                fontWeight: 700,
                color: "primary.main"
              }}>
              {t('learn.twcore.profiles.title')}
            </Typography>
            <Typography
              variant="body2"
              sx={{
                color: "text.secondary",
                mb: 2,
                lineHeight: 1.7
              }}>
              {t('learn.twcore.profiles.content')}
            </Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>{t('learn.twcore.headers.resource')}</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>{t('learn.twcore.headers.profile')}</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>{t('learn.twcore.headers.description')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {PROFILES.map(({ resource, profile, descKey }) => (
                    <TableRow key={resource}>
                      <TableCell>
                        <Chip label={resource} size="small" variant="outlined" sx={{ fontFamily: 'monospace', fontWeight: 600 }} />
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'primary.main' }}>
                          {profile}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{
                          color: "text.secondary"
                        }}>
                          {t(`learn.twcore.profiles.${descKey}`)}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </Grid>

        {/* Code Systems */}
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: CARD_RADIUS, border: '1px solid', borderColor: 'divider', height: '100%' }}>
            <Typography
              variant="h6"
              gutterBottom
              sx={{
                fontWeight: 700,
                color: "primary.main"
              }}>
              {t('learn.twcore.codeSystems.title')}
            </Typography>
            <Typography
              variant="body2"
              sx={{
                color: "text.secondary",
                mb: 2,
                lineHeight: 1.7
              }}>
              {t('learn.twcore.codeSystems.content')}
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              {CODE_SYSTEMS.map(({ key }) => (
                <Box key={key} sx={{ p: 1.5, borderRadius: INNER_RADIUS, bgcolor: 'action.hover' }}>
                  <Typography variant="body2" sx={{ lineHeight: 1.6 }}>
                    {t(`learn.twcore.codeSystems.${key}`)}
                  </Typography>
                </Box>
              ))}
            </Box>
          </Paper>
        </Grid>

        {/* Full Example */}
        <Grid size={12}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: CARD_RADIUS, border: '1px solid', borderColor: 'divider' }}>
            <Typography
              variant="h6"
              gutterBottom
              sx={{
                fontWeight: 700,
                color: "primary.main"
              }}>
              {t('learn.twcore.example.title')}
            </Typography>
            <Typography
              variant="body2"
              sx={{
                color: "text.secondary",
                mb: 2,
                lineHeight: 1.7
              }}>
              {t('learn.twcore.example.description')}
            </Typography>
            <CodeBlock code={CQL_EXAMPLE_DIABETES} maxHeight={500} />
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}

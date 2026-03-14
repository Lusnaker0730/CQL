import { Box, Typography, Paper, Grid, List, ListItem, ListItemIcon, ListItemText, Alert } from '@mui/material'
import {
  CheckCircle as CheckIcon,
  Error as ProblemIcon,
  AccountTree as RelationIcon,
  MedicalServices as AppIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'

export default function CqlIntroduction() {
  const { t } = useTranslation('landing')

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} gutterBottom color="secondary.main">
        {t('learn.introduction.title')}
      </Typography>

      <Grid container spacing={3} sx={{ mt: 1 }}>
        {/* What is CQL */}
        <Grid item xs={12}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
            <Typography variant="h6" fontWeight={700} gutterBottom color="primary.main">
              {t('learn.introduction.what.title')}
            </Typography>
            <Typography variant="body1" color="text.secondary" sx={{ lineHeight: 1.8 }}>
              {t('learn.introduction.what.content')}
            </Typography>
          </Paper>
        </Grid>

        {/* Why CQL */}
        <Grid item xs={12} md={6}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid', borderColor: 'divider', height: '100%' }}>
            <Typography variant="h6" fontWeight={700} gutterBottom color="primary.main">
              {t('learn.introduction.why.title')}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              {t('learn.introduction.why.problems')}
            </Typography>
            <List dense disablePadding>
              {['problem1', 'problem2', 'problem3'].map((key) => (
                <ListItem key={key} disableGutters sx={{ py: 0.5 }}>
                  <ListItemIcon sx={{ minWidth: 32 }}>
                    <ProblemIcon sx={{ color: 'error.main', fontSize: 20 }} />
                  </ListItemIcon>
                  <ListItemText
                    primary={t(`learn.introduction.why.${key}`)}
                    primaryTypographyProps={{ variant: 'body2' }}
                  />
                </ListItem>
              ))}
            </List>
            <Alert severity="success" sx={{ mt: 2 }} icon={<CheckIcon />}>
              {t('learn.introduction.why.solution')}
            </Alert>
          </Paper>
        </Grid>

        {/* CQL + FHIR Relationship */}
        <Grid item xs={12} md={6}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid', borderColor: 'divider', height: '100%' }}>
            <Typography variant="h6" fontWeight={700} gutterBottom color="primary.main">
              <RelationIcon sx={{ mr: 1, verticalAlign: 'middle', fontSize: 22 }} />
              {t('learn.introduction.relationship.title')}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2, lineHeight: 1.8 }}>
              {t('learn.introduction.relationship.content')}
            </Typography>
            {['fhirModel', 'cqlLogic', 'together'].map((key) => (
              <Box key={key} sx={{ display: 'flex', alignItems: 'flex-start', gap: 1, mb: 1 }}>
                <CheckIcon sx={{ color: 'primary.main', fontSize: 18, mt: 0.3 }} />
                <Typography variant="body2">
                  {t(`learn.introduction.relationship.${key}`)}
                </Typography>
              </Box>
            ))}
          </Paper>
        </Grid>

        {/* Application Scenarios */}
        <Grid item xs={12}>
          <Paper elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
            <Typography variant="h6" fontWeight={700} gutterBottom color="primary.main">
              <AppIcon sx={{ mr: 1, verticalAlign: 'middle', fontSize: 22 }} />
              {t('learn.introduction.applications.title')}
            </Typography>
            <Grid container spacing={2} sx={{ mt: 0.5 }}>
              {(['ecqm', 'cds', 'phenotyping', 'research'] as const).map((key) => (
                <Grid item xs={12} sm={6} key={key}>
                  <Box
                    sx={{
                      p: 2,
                      borderRadius: 2,
                      bgcolor: 'action.hover',
                      height: '100%',
                    }}
                  >
                    <Typography variant="body2" sx={{ lineHeight: 1.7 }}>
                      {t(`learn.introduction.applications.${key}`)}
                    </Typography>
                  </Box>
                </Grid>
              ))}
            </Grid>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  )
}

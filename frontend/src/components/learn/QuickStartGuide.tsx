import { Box, Typography, Paper, Stepper, Step, StepLabel, StepContent, Alert, List, ListItem, ListItemIcon, ListItemText } from '@mui/material'
import {
  ArrowForward as ArrowIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import CodeBlock from './CodeBlock'

const STEPS = ['step1', 'step2', 'step3', 'step4', 'step5'] as const

export default function QuickStartGuide() {
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
        {t('learn.quickStart.title')}
      </Typography>
      <Typography
        variant="body1"
        sx={{
          color: "text.secondary",
          mb: 4
        }}>
        {t('learn.quickStart.subtitle')}
      </Typography>
      <Paper elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid', borderColor: 'divider', mb: 3 }}>
        <Stepper orientation="vertical">
          {STEPS.map((key, index) => (
            <Step key={key} active expanded>
              <StepLabel
                slotProps={{
                  stepIcon: {
                    sx: {
                      color: 'primary.main',
                      '&.Mui-active': { color: 'primary.main' },
                      fontSize: 28,
                    },
                  }
                }}
              >
                <Typography variant="subtitle1" sx={{
                  fontWeight: 700
                }}>
                  {t(`learn.quickStart.${key}.title`)}
                </Typography>
              </StepLabel>
              <StepContent>
                <Typography
                  variant="body2"
                  sx={{
                    color: "text.secondary",
                    lineHeight: 1.8,
                    py: 1
                  }}>
                  {t(`learn.quickStart.${key}.content`)}
                </Typography>
                {index === 2 && (
                  <Box sx={{ mt: 1 }}>
                    <CodeBlock
                      code={`library MyFirstCQL version '1.0.0'\n\nusing FHIR version '4.0.1'\ninclude FHIRHelpers version '4.0.1'\n\ncontext Patient\n\ndefine "Patient Name":\n  Patient.name`}
                    />
                  </Box>
                )}
              </StepContent>
            </Step>
          ))}
        </Stepper>
      </Paper>
      <Alert severity="info" sx={{ borderRadius: 3 }}>
        <Typography variant="subtitle2" gutterBottom sx={{
          fontWeight: 700
        }}>
          {t('learn.quickStart.nextSteps')}
        </Typography>
        <Typography variant="body2" sx={{ mb: 1.5 }}>
          {t('learn.quickStart.nextStepsContent')}
        </Typography>
        <List dense disablePadding>
          {(['next1', 'next2', 'next3'] as const).map((key) => (
            <ListItem key={key} disableGutters sx={{ py: 0.25 }}>
              <ListItemIcon sx={{ minWidth: 28 }}>
                <ArrowIcon sx={{ fontSize: 16, color: 'info.main' }} />
              </ListItemIcon>
              <ListItemText primary={t(`learn.quickStart.${key}`)} slotProps={{
                primary: { variant: 'body2' }
              }} />
            </ListItem>
          ))}
        </List>
      </Alert>
    </Box>
  );
}

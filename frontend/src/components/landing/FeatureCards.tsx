import { Box, Typography, Paper, Grid } from '@mui/material'
import {
  Code as EditorIcon,
  Assessment as MeasureIcon,
  Psychology as CdsIcon,
  Storage as FhirIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'

const FEATURES = [
  { key: 'editor', icon: EditorIcon, color: '#0D7377' },
  { key: 'measures', icon: MeasureIcon, color: '#1B3A5C' },
  { key: 'cds', icon: CdsIcon, color: '#14A3A8' },
  { key: 'fhir', icon: FhirIcon, color: '#E8A838' },
] as const

export default function FeatureCards() {
  const { t } = useTranslation('landing')

  return (
    <Box sx={{ py: 8, px: 3 }}>
      <Typography
        variant="h4"
        gutterBottom
        sx={{
          fontWeight: 700,
          textAlign: "center",
          color: "secondary.main"
        }}>
        {t('features.title')}
      </Typography>
      <Typography
        variant="body1"
        sx={{
          textAlign: "center",
          color: "text.secondary",
          mb: 5,
          maxWidth: 600,
          mx: 'auto'
        }}>
        {t('features.subtitle')}
      </Typography>
      <Grid
        container
        spacing={3}
        sx={{
          justifyContent: "center",
          maxWidth: 1100,
          mx: 'auto'
        }}>
        {FEATURES.map(({ key, icon: Icon, color }) => (
          <Grid size={{ xs: 12, sm: 6, md: 3 }} key={key}>
            <Paper
              elevation={0}
              sx={{
                p: 3,
                height: '100%',
                borderRadius: 3,
                border: '1px solid',
                borderColor: 'divider',
                transition: 'all 0.3s ease',
                '&:hover': {
                  transform: 'translateY(-4px)',
                  boxShadow: `0 8px 24px ${color}22`,
                  borderColor: color,
                },
              }}
            >
              <Box
                sx={{
                  width: 48,
                  height: 48,
                  borderRadius: '12px',
                  bgcolor: `${color}15`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  mb: 2,
                }}
              >
                <Icon sx={{ fontSize: 26, color }} />
              </Box>
              <Typography variant="subtitle1" gutterBottom sx={{
                fontWeight: 700
              }}>
                {t(`features.${key}.title`)}
              </Typography>
              <Typography
                variant="body2"
                sx={{
                  color: "text.secondary",
                  lineHeight: 1.7
                }}>
                {t(`features.${key}.description`)}
              </Typography>
            </Paper>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}

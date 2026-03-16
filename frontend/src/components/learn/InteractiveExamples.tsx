import { useState, useCallback } from 'react'
import { Box, Typography, Paper, Grid, List, ListItemButton, ListItemIcon, ListItemText, Button } from '@mui/material'
import { alpha, useTheme } from '@mui/material/styles'
import {
  Bloodtype as DiabetesIcon,
  MonitorHeart as HypertensionIcon,
  Medication as MedicationIcon,
  EventNote as EncounterIcon,
  OpenInNew as OpenInEditorIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { setCqlContent } from '../../store/editorSlice'
import CodeBlock from './CodeBlock'
import {
  CQL_EXAMPLE_DIABETES,
  CQL_EXAMPLE_HYPERTENSION,
  CQL_EXAMPLE_MEDICATION,
  CQL_EXAMPLE_ENCOUNTER,
} from '../../constants/cqlExamples'

const EXAMPLE_KEYS = ['diabetes', 'hypertension', 'medication', 'encounter'] as const
const EXAMPLE_CODES = [CQL_EXAMPLE_DIABETES, CQL_EXAMPLE_HYPERTENSION, CQL_EXAMPLE_MEDICATION, CQL_EXAMPLE_ENCOUNTER]
const EXAMPLE_ICONS = [DiabetesIcon, HypertensionIcon, MedicationIcon, EncounterIcon]

export default function InteractiveExamples() {
  const { t } = useTranslation('landing')
  const theme = useTheme()
  const navigate = useNavigate()
  const dispatch = useDispatch()
  const [selected, setSelected] = useState(0)

  const EXAMPLE_COLORS = [
    theme.palette.error.main,
    theme.palette.primary.main,
    theme.palette.warning.main,
    theme.palette.secondary.main,
  ]

  const EXAMPLES = EXAMPLE_KEYS.map((key, i) => ({
    key,
    icon: EXAMPLE_ICONS[i],
    code: EXAMPLE_CODES[i],
    color: EXAMPLE_COLORS[i],
  }))

  const handleSelect = useCallback((index: number) => {
    setSelected(index)
  }, [])

  const handleOpenInEditor = useCallback(() => {
    dispatch(setCqlContent(EXAMPLES[selected].code))
    navigate('/')
  }, [dispatch, navigate, selected, EXAMPLES])

  const example = EXAMPLES[selected]

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} gutterBottom color="secondary.main">
        {t('learn.examples.title')}
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        {t('learn.examples.subtitle')}
      </Typography>

      <Grid container spacing={3}>
        {/* Example List */}
        <Grid size={{ xs: 12, md: 4 }}>
          <Paper elevation={0} sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }}>
            <List disablePadding>
              {EXAMPLES.map(({ key, icon: Icon, color }, index) => (
                <ListItemButton
                  key={key}
                  selected={selected === index}
                  onClick={() => handleSelect(index)}
                  sx={{
                    py: 2,
                    borderLeft: '3px solid',
                    borderColor: selected === index ? color : 'transparent',
                    '&.Mui-selected': { bgcolor: alpha(color, 0.03) },
                  }}
                >
                  <ListItemIcon sx={{ minWidth: 40 }}>
                    <Icon sx={{ color: selected === index ? color : 'text.secondary' }} />
                  </ListItemIcon>
                  <ListItemText
                    primary={t(`learn.examples.${key}.title`)}
                    secondary={t(`learn.examples.${key}.description`)}
                    primaryTypographyProps={{ fontWeight: selected === index ? 700 : 500 }}
                    secondaryTypographyProps={{
                      variant: 'caption',
                      sx: { lineHeight: 1.5, mt: 0.5, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' },
                    }}
                  />
                </ListItemButton>
              ))}
            </List>
          </Paper>
        </Grid>

        {/* Code + Explanation */}
        <Grid size={{ xs: 12, md: 8 }}>
          <Paper elevation={0} sx={{ borderRadius: 3, border: '1px solid', borderColor: 'divider', overflow: 'hidden' }}>
            <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="subtitle1" fontWeight={700} sx={{ flex: 1 }}>
                {t(`learn.examples.${example.key}.title`)}
              </Typography>
              <Button
                size="small"
                variant="outlined"
                startIcon={<OpenInEditorIcon />}
                onClick={handleOpenInEditor}
                sx={{ textTransform: 'none' }}
              >
                {t('learn.examples.openInEditor')}
              </Button>
            </Box>
            <CodeBlock code={example.code} maxHeight={400} />
            <Box sx={{ p: 2.5, bgcolor: 'action.hover' }}>
              <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.8 }}>
                {t(`learn.examples.${example.key}.explanation`)}
              </Typography>
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  )
}

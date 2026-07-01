import { Box, Typography, Paper, Grid } from '@mui/material'
import { useTranslation } from 'react-i18next'
import CodeBlock from './CodeBlock'
import {
  CQL_CONCEPT_LIBRARY,
  CQL_CONCEPT_VALUESET,
  CQL_CONCEPT_CONTEXT,
  CQL_CONCEPT_RETRIEVE,
  CQL_CONCEPT_QUERY,
  CQL_CONCEPT_DEFINE,
} from '../../constants/cqlExamples'

const CONCEPTS = [
  { key: 'library', code: CQL_CONCEPT_LIBRARY },
  { key: 'valueset', code: CQL_CONCEPT_VALUESET },
  { key: 'context', code: CQL_CONCEPT_CONTEXT },
  { key: 'retrieve', code: CQL_CONCEPT_RETRIEVE },
  { key: 'query', code: CQL_CONCEPT_QUERY },
  { key: 'define', code: CQL_CONCEPT_DEFINE },
] as const

export default function ConceptGuide() {
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
        {t('learn.concepts.title')}
      </Typography>
      <Grid container spacing={3} sx={{ mt: 1 }}>
        {CONCEPTS.map(({ key, code }, index) => (
          <Grid size={{ xs: 12, md: 6 }} key={key}>
            <Paper
              elevation={0}
              sx={{
                borderRadius: 3,
                border: '1px solid',
                borderColor: 'divider',
                overflow: 'hidden',
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
              }}
            >
              <Box sx={{ p: 2.5, pb: 1.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
                  <Box
                    sx={{
                      width: 28,
                      height: 28,
                      borderRadius: '8px',
                      bgcolor: 'primary.main',
                      color: 'white',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '0.85rem',
                      fontWeight: 700,
                    }}
                  >
                    {index + 1}
                  </Box>
                  <Typography variant="subtitle1" sx={{
                    fontWeight: 700
                  }}>
                    {t(`learn.concepts.${key}.title`)}
                  </Typography>
                </Box>
                <Typography
                  variant="body2"
                  sx={{
                    color: "text.secondary",
                    lineHeight: 1.7,
                    mb: 1.5
                  }}>
                  {t(`learn.concepts.${key}.content`)}
                </Typography>
              </Box>

              <Box sx={{ px: 2.5, pb: 1.5, flex: 1 }}>
                <CodeBlock code={code} maxHeight={180} />
              </Box>

              <Box sx={{ px: 2.5, pb: 2.5 }}>
                <Typography
                  variant="caption"
                  sx={{
                    color: "text.secondary",
                    display: 'block',
                    bgcolor: 'action.hover',
                    p: 1.5,
                    borderRadius: 1.5,
                    lineHeight: 1.6
                  }}>
                  {t(`learn.concepts.${key}.explanation`)}
                </Typography>
              </Box>
            </Paper>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}

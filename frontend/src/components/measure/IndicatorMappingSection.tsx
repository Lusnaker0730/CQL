import { useState } from 'react'
import {
  Accordion,
  AccordionSummary,
  AccordionDetails,
  TextField,
  InputAdornment,
  Stack,
  Typography,
  IconButton,
  Tooltip,
  Box,
  Chip,
  MenuItem,
} from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import SearchIcon from '@mui/icons-material/Search'
import LinkIcon from '@mui/icons-material/Link'
import { useTranslation } from 'react-i18next'
import type { MeasureDefinition, IndicatorCatalogEntry } from '../../types'
import IndicatorCatalogDialog from './IndicatorCatalogDialog'

interface IndicatorMappingSectionProps {
  measure: MeasureDefinition
  onChange: (updates: Partial<MeasureDefinition>) => void
  readOnly?: boolean
}

const CATEGORIES = [
  'Outcome',
  'Process',
  'Structure',
  'Efficiency',
  'Patient Experience',
  'Safety',
]

export default function IndicatorMappingSection({
  measure,
  onChange,
  readOnly,
}: IndicatorMappingSectionProps) {
  const { t } = useTranslation('measures')
  const [catalogOpen, setCatalogOpen] = useState(false)
  const [catalogSource, setCatalogSource] = useState<string | undefined>()

  const hasMappings = !!(
    measure.mohIndicatorCode ||
    measure.nhiaP4pCode ||
    measure.drgIndicatorCode
  )

  const handleBrowse = (source?: string) => {
    setCatalogSource(source)
    setCatalogOpen(true)
  }

  const handleCatalogSelect = (entry: IndicatorCatalogEntry) => {
    const updates: Partial<MeasureDefinition> = {
      indicatorCategory: entry.category || measure.indicatorCategory,
    }
    switch (entry.source) {
      case 'MOH':
        updates.mohIndicatorCode = entry.code
        break
      case 'NHIA_P4P':
        updates.nhiaP4pCode = entry.code
        break
      case 'DRG':
        updates.drgIndicatorCode = entry.code
        break
      case 'CMS':
        updates.mohIndicatorCode = entry.code
        break
    }
    onChange(updates)
  }

  return (
    <>
      <Accordion defaultExpanded={hasMappings}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Stack direction="row" alignItems="center" spacing={1}>
            <LinkIcon fontSize="small" color="primary" />
            <Typography fontWeight={600}>
              {t('indicators.title')}
            </Typography>
            {hasMappings && (
              <Chip label={t('indicators.mapped')} size="small" color="success" />
            )}
          </Stack>
        </AccordionSummary>
        <AccordionDetails>
          <Stack spacing={2}>
            <Stack direction="row" spacing={2} alignItems="flex-start">
              <TextField
                label={t('indicators.mohCode')}
                value={measure.mohIndicatorCode || ''}
                onChange={(e) => onChange({ mohIndicatorCode: e.target.value })}
                disabled={readOnly}
                size="small"
                sx={{ flex: 1 }}
                placeholder="e.g. MOHW-QI-001"
                InputProps={{
                  endAdornment: !readOnly ? (
                    <InputAdornment position="end">
                      <Tooltip title={t('indicators.browse')}>
                        <IconButton size="small" onClick={() => handleBrowse('MOH')}>
                          <SearchIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </InputAdornment>
                  ) : undefined,
                }}
              />
              <TextField
                label={t('indicators.nhiaCode')}
                value={measure.nhiaP4pCode || ''}
                onChange={(e) => onChange({ nhiaP4pCode: e.target.value })}
                disabled={readOnly}
                size="small"
                sx={{ flex: 1 }}
                placeholder="e.g. P4P-DM-001"
                InputProps={{
                  endAdornment: !readOnly ? (
                    <InputAdornment position="end">
                      <Tooltip title={t('indicators.browse')}>
                        <IconButton size="small" onClick={() => handleBrowse('NHIA_P4P')}>
                          <SearchIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </InputAdornment>
                  ) : undefined,
                }}
              />
            </Stack>
            <Stack direction="row" spacing={2} alignItems="flex-start">
              <TextField
                label={t('indicators.drgCode')}
                value={measure.drgIndicatorCode || ''}
                onChange={(e) => onChange({ drgIndicatorCode: e.target.value })}
                disabled={readOnly}
                size="small"
                sx={{ flex: 1 }}
                placeholder="e.g. DRG-001"
                InputProps={{
                  endAdornment: !readOnly ? (
                    <InputAdornment position="end">
                      <Tooltip title={t('indicators.browse')}>
                        <IconButton size="small" onClick={() => handleBrowse('DRG')}>
                          <SearchIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </InputAdornment>
                  ) : undefined,
                }}
              />
              <TextField
                label={t('indicators.category')}
                value={measure.indicatorCategory || ''}
                onChange={(e) => onChange({ indicatorCategory: e.target.value })}
                disabled={readOnly}
                size="small"
                select
                sx={{ flex: 1 }}
              >
                <MenuItem value="">
                  <em>{t('indicators.noCategory')}</em>
                </MenuItem>
                {CATEGORIES.map((c) => (
                  <MenuItem key={c} value={c}>
                    {c}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
            {!readOnly && (
              <Box>
                <Typography variant="caption" color="text.secondary">
                  {t('indicators.hint')}
                </Typography>
              </Box>
            )}
          </Stack>
        </AccordionDetails>
      </Accordion>

      <IndicatorCatalogDialog
        open={catalogOpen}
        onClose={() => setCatalogOpen(false)}
        onSelect={handleCatalogSelect}
        sourceFilter={catalogSource}
      />
    </>
  )
}

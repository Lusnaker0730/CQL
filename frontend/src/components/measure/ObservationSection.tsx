import React from 'react';
import {
  Autocomplete,
  TextField,
  Paper,
  Stack,
  Typography,
  IconButton,
  Button,
  Box,
} from '@mui/material';
import { Add, Delete } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

/** The backend's canonical aggregate methods (`MeasureScoreCalculator`); the stored value is the key, the option shows its label. */
const AGGREGATE_METHOD_KEYS = ['count', 'sum', 'average', 'median', 'minimum', 'maximum', 'percentile'] as const;

interface Observation {
  criteriaExpression: string;
  aggregateMethod: string;
  /** PAT-264: the percentile (0–100) for the `percentile` method. */
  percentileValue?: number | null;
  populationRef: string;
  description?: string;
}

interface ObservationSectionProps {
  observations: Observation[] | undefined;
  onChange: (value: Observation[]) => void;
  expressionNames: string[];
  populationTypes: string[];
  readOnly?: boolean;
}

const ObservationSection: React.FC<ObservationSectionProps> = ({
  observations,
  onChange,
  expressionNames,
  populationTypes,
  readOnly = false,
}) => {
  const { t } = useTranslation('measures');
  const items = observations ?? [];

  const methodLabel = (key: string) => t(`observations.aggregateMethods.${key}`, { defaultValue: key });

  const handleAdd = () => {
    onChange([
      ...items,
      { criteriaExpression: '', aggregateMethod: '', populationRef: '', description: '' },
    ]);
  };

  const handleRemove = (index: number) => {
    const updated = items.filter((_, i) => i !== index);
    onChange(updated);
  };

  const handleFieldChange = (index: number, field: keyof Observation, value: string | number | null | undefined) => {
    const updated = items.map((item, i) => {
      if (i !== index) return item;
      const next = { ...item, [field]: value } as Observation;
      // the percentile only belongs to the percentile method
      if (field === 'aggregateMethod' && value !== 'percentile') next.percentileValue = undefined;
      return next;
    });
    onChange(updated);
  };

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Typography variant="subtitle1" sx={{
          fontWeight: 600
        }}>
          {t('observations.title')}
        </Typography>

        {items.map((item, index) => (
          <Paper key={`obs-${item.criteriaExpression || index}-${item.aggregateMethod}`} variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={2}>
              <Stack
                direction="row"
                spacing={1}
                sx={{
                  alignItems: "center",
                  justifyContent: "space-between"
                }}>
                <Typography variant="body2" sx={{
                  fontWeight: 500
                }}>
                  {t('observations.observationLabel', { number: index + 1 })}
                </Typography>
                {!readOnly && (
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => handleRemove(index)}
                    aria-label={t('observations.removeObservation', { number: index + 1 })}
                  >
                    <Delete fontSize="small" />
                  </IconButton>
                )}
              </Stack>

              <Autocomplete
                options={expressionNames}
                value={item.criteriaExpression || null}
                onChange={(_event, newValue) =>
                  handleFieldChange(index, 'criteriaExpression', newValue ?? '')
                }
                renderInput={(params) => (
                  <TextField {...params} size="small" label={t('observations.fields.cqlExpression')} />
                )}
                disabled={readOnly}
              />

              <Autocomplete
                options={[...AGGREGATE_METHOD_KEYS] as string[]}
                getOptionLabel={methodLabel}
                value={item.aggregateMethod || null}
                onChange={(_event, newValue) =>
                  handleFieldChange(index, 'aggregateMethod', newValue ?? '')
                }
                renderInput={(params) => (
                  <TextField {...params} size="small" label={t('observations.fields.aggregateMethod')} />
                )}
                disabled={readOnly}
              />

              {item.aggregateMethod === 'percentile' && (
                <TextField
                  size="small"
                  type="number"
                  label={t('observations.fields.percentileValue')}
                  helperText={t('observations.fields.percentileHelp')}
                  value={item.percentileValue ?? ''}
                  onChange={(e) => handleFieldChange(index, 'percentileValue', e.target.value === '' ? null : Number(e.target.value))}
                  error={item.percentileValue == null || item.percentileValue < 0 || item.percentileValue > 100}
                  slotProps={{ htmlInput: { min: 0, max: 100, step: 1, 'data-testid': 'observation-percentile' } }}
                  disabled={readOnly}
                />
              )}

              <Autocomplete
                options={populationTypes}
                value={item.populationRef || null}
                onChange={(_event, newValue) =>
                  handleFieldChange(index, 'populationRef', newValue ?? '')
                }
                renderInput={(params) => (
                  <TextField {...params} size="small" label={t('observations.fields.populationReference')} />
                )}
                disabled={readOnly}
              />

              <TextField
                size="small"
                label={t('observations.fields.description')}
                value={item.description ?? ''}
                onChange={(e) => handleFieldChange(index, 'description', e.target.value)}
                fullWidth
                disabled={readOnly}
              />
            </Stack>
          </Paper>
        ))}

        {!readOnly && (
          <Box>
            <Button
              variant="outlined"
              size="small"
              startIcon={<Add />}
              onClick={handleAdd}
            >
              {t('observations.addObservation')}
            </Button>
          </Box>
        )}
      </Stack>
    </Paper>
  );
};

export default ObservationSection;

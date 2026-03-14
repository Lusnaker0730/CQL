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

const AGGREGATE_METHOD_KEYS = ['count', 'sum', 'average', 'median', 'minimum', 'maximum'] as const;

interface Observation {
  criteriaExpression: string;
  aggregateMethod: string;
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

  const AGGREGATE_METHODS = AGGREGATE_METHOD_KEYS.map((key) => t(`observations.aggregateMethods.${key}`));

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

  const handleFieldChange = (index: number, field: keyof Observation, value: string) => {
    const updated = items.map((item, i) =>
      i === index ? { ...item, [field]: value } : item
    );
    onChange(updated);
  };

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Typography variant="subtitle1" fontWeight={600}>
          {t('observations.title')}
        </Typography>

        {items.map((item, index) => (
          <Paper key={`obs-${item.criteriaExpression || index}-${item.aggregateMethod}`} variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={2}>
              <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                <Typography variant="body2" fontWeight={500}>
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
                options={AGGREGATE_METHODS}
                value={item.aggregateMethod || null}
                onChange={(_event, newValue) =>
                  handleFieldChange(index, 'aggregateMethod', newValue ?? '')
                }
                renderInput={(params) => (
                  <TextField {...params} size="small" label={t('observations.fields.aggregateMethod')} />
                )}
                disabled={readOnly}
              />

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

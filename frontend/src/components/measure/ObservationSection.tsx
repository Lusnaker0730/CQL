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

const AGGREGATE_METHODS = ['Count', 'Sum', 'Average', 'Median', 'Minimum', 'Maximum'];

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
  const items = observations ?? [];

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
          Observation Criteria
        </Typography>

        {items.map((item, index) => (
          <Paper key={index} variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={2}>
              <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                <Typography variant="body2" fontWeight={500}>
                  Observation {index + 1}
                </Typography>
                {!readOnly && (
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => handleRemove(index)}
                    aria-label={`Remove observation ${index + 1}`}
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
                  <TextField {...params} size="small" label="CQL Expression" />
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
                  <TextField {...params} size="small" label="Aggregate Method" />
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
                  <TextField {...params} size="small" label="Population Reference" />
                )}
                disabled={readOnly}
              />

              <TextField
                size="small"
                label="Description"
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
              Add Observation
            </Button>
          </Box>
        )}
      </Stack>
    </Paper>
  );
};

export default ObservationSection;

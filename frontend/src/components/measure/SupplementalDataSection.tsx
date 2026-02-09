import React from 'react';
import {
  Autocomplete,
  TextField,
  Paper,
  Stack,
  Typography,
  IconButton,
  Chip,
} from '@mui/material';
import { Delete } from '@mui/icons-material';

interface SupplementalDataElement {
  definition: string;
  description: string;
}

interface SupplementalDataSectionProps {
  supplementalData: SupplementalDataElement[] | undefined;
  onChange: (value: SupplementalDataElement[]) => void;
  expressionNames: string[];
  readOnly?: boolean;
}

const SupplementalDataSection: React.FC<SupplementalDataSectionProps> = ({
  supplementalData,
  onChange,
  expressionNames,
  readOnly = false,
}) => {
  const items = supplementalData ?? [];

  const selectedDefinitions = items.map((item) => item.definition);
  const availableExpressions = expressionNames.filter(
    (name) => !selectedDefinitions.includes(name)
  );

  const handleAdd = (definition: string) => {
    if (!definition) return;
    onChange([...items, { definition, description: '' }]);
  };

  const handleRemove = (index: number) => {
    const updated = items.filter((_, i) => i !== index);
    onChange(updated);
  };

  const handleDescriptionChange = (index: number, description: string) => {
    const updated = items.map((item, i) =>
      i === index ? { ...item, description } : item
    );
    onChange(updated);
  };

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Typography variant="subtitle1" fontWeight={600}>
          Supplemental Data Elements
        </Typography>

        {items.map((item, index) => (
          <Stack key={item.definition} direction="row" spacing={1} alignItems="flex-start">
            <Chip
              label={item.definition}
              sx={{ mt: 1, minWidth: 160 }}
            />
            <TextField
              size="small"
              label="Description"
              value={item.description}
              onChange={(e) => handleDescriptionChange(index, e.target.value)}
              inputProps={{ maxLength: 128 }}
              fullWidth
              disabled={readOnly}
            />
            {!readOnly && (
              <IconButton
                size="small"
                color="error"
                onClick={() => handleRemove(index)}
                aria-label={`Remove ${item.definition}`}
              >
                <Delete fontSize="small" />
              </IconButton>
            )}
          </Stack>
        ))}

        {!readOnly && (
          <Autocomplete
            options={availableExpressions}
            value={null}
            onChange={(_event, newValue) => {
              if (newValue) {
                handleAdd(newValue);
              }
            }}
            renderInput={(params) => (
              <TextField
                {...params}
                size="small"
                label="Add supplemental data element"
                placeholder="Select a CQL expression"
              />
            )}
            blurOnSelect
            clearOnBlur
          />
        )}
      </Stack>
    </Paper>
  );
};

export default SupplementalDataSection;

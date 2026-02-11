import { useState } from 'react'
import {
  Box,
  Typography,
  List,
  ListItemButton,
  ListItemText,
  TextField,
  InputAdornment,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material'
import {
  Search as SearchIcon,
  ExpandMore as ExpandMoreIcon,
} from '@mui/icons-material'
import type { FormTemplateCategory, FormTemplate } from '../../../types/authoring'

interface ElementSelectDropdownProps {
  templates: FormTemplateCategory[]
  onSelect: (template: FormTemplate) => void
}

export default function ElementSelectDropdown({ templates, onSelect }: ElementSelectDropdownProps) {
  const [search, setSearch] = useState('')

  const visibleCategories = templates
    .filter((cat) => !cat.suppress && cat.entries.length > 0)
    .map((cat) => ({
      ...cat,
      entries: cat.entries.filter((entry) => {
        if (entry.suppress) return false
        if (!search) return true
        return entry.name.toLowerCase().includes(search.toLowerCase())
      }),
    }))
    .filter((cat) => cat.entries.length > 0)

  return (
    <Box sx={{ width: 320, maxHeight: 400, overflow: 'auto' }}>
      <Box sx={{ p: 1, position: 'sticky', top: 0, backgroundColor: 'background.paper', zIndex: 1 }}>
        <TextField
          size="small"
          fullWidth
          placeholder="Search elements..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          autoFocus
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
      </Box>

      {visibleCategories.length === 0 ? (
        <Box sx={{ p: 2, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary">
            No elements found
          </Typography>
        </Box>
      ) : (
        visibleCategories.map((category) => (
          <Accordion
            key={category.id}
            disableGutters
            elevation={0}
            defaultExpanded={visibleCategories.length === 1 || !!search}
            sx={{ '&:before': { display: 'none' } }}
          >
            <AccordionSummary expandIcon={<ExpandMoreIcon />} sx={{ minHeight: 36, py: 0 }}>
              <Typography variant="subtitle2">{category.name}</Typography>
            </AccordionSummary>
            <AccordionDetails sx={{ p: 0 }}>
              <List dense disablePadding>
                {category.entries.map((entry) => (
                  <ListItemButton
                    key={entry.id}
                    onClick={() => onSelect(entry)}
                    sx={{ pl: 4 }}
                  >
                    <ListItemText
                      primary={entry.name}
                      secondary={getElementDescription(entry, category.name)}
                      primaryTypographyProps={{ variant: 'body2' }}
                      secondaryTypographyProps={{ variant: 'caption' }}
                    />
                  </ListItemButton>
                ))}
              </List>
            </AccordionDetails>
          </Accordion>
        ))
      )}
    </Box>
  )
}

const ELEMENT_DESCRIPTIONS: Record<string, string> = {
  AgeRange: 'Filter by patient age range',
  Gender: 'Filter by patient gender',
  GenericObservation_vsac: 'Lab results, vital signs, or other observations',
  GenericCondition_vsac: 'Diagnoses, problems, or health conditions',
  GenericMedicationStatement_vsac: 'Reported medication usage',
  GenericMedicationRequest_vsac: 'Medication prescriptions or orders',
  GenericProcedure_vsac: 'Surgical or clinical procedures',
  GenericEncounter_vsac: 'Patient visits or encounters',
  GenericAllergyIntolerance_vsac: 'Allergies or intolerances',
  GenericImmunization_vsac: 'Vaccination records',
  GenericServiceRequest_vsac: 'Diagnostic or service requests',
  GenericDevice_vsac: 'Medical devices',
  BooleanParameter: 'Configurable true/false parameter',
}

function getElementDescription(entry: FormTemplate, categoryName: string): string {
  return ELEMENT_DESCRIPTIONS[entry.id] || entry.returnType?.replace(/_/g, ' ') || categoryName
}

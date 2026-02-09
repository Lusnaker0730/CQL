import { useState } from 'react'
import { Button, Menu, MenuItem, ListItemIcon, ListItemText } from '@mui/material'
import { Add as AddIcon, Person, LocalHospital, MedicalServices } from '@mui/icons-material'
import { useFhirResourceTypes } from '../../hooks/useFhirMetadata'
import { useBundleBuilder } from '../../contexts/BundleBuilderContext'

const RESOURCE_ICONS: Record<string, React.ReactNode> = {
  Patient: <Person fontSize="small" />,
  Encounter: <LocalHospital fontSize="small" />,
}
const DEFAULT_ICON = <MedicalServices fontSize="small" />

interface AddResourceButtonProps {
  onDirty: () => void
}

export default function AddResourceButton({ onDirty }: AddResourceButtonProps) {
  const { data: resourceTypes } = useFhirResourceTypes()
  const { state, dispatch } = useBundleBuilder()
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)

  const hasPatient = state.entries.some((e) => e.resourceType === 'Patient')

  const handleAdd = (resourceType: string) => {
    const id = resourceType.toLowerCase() + '-' + crypto.randomUUID().slice(0, 8)
    dispatch({
      type: 'ADD_ENTRY',
      payload: {
        id,
        resourceType,
        resourceData: { id },
      },
    })
    onDirty()
    setAnchorEl(null)
  }

  return (
    <>
      <Button
        variant="outlined"
        size="small"
        startIcon={<AddIcon />}
        onClick={(e) => setAnchorEl(e.currentTarget)}
        sx={{ textTransform: 'none' }}
      >
        Add Resource
      </Button>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
        slotProps={{ paper: { sx: { maxHeight: 320 } } }}
      >
        {(resourceTypes || []).map((type) => (
          <MenuItem
            key={type}
            onClick={() => handleAdd(type)}
            disabled={type === 'Patient' && hasPatient}
          >
            <ListItemIcon>
              {RESOURCE_ICONS[type] || DEFAULT_ICON}
            </ListItemIcon>
            <ListItemText>{type}</ListItemText>
          </MenuItem>
        ))}
      </Menu>
    </>
  )
}

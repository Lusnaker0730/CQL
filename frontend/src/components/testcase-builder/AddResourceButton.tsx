import { useState } from 'react'
import { Button, Menu, MenuItem, ListItemIcon, ListItemText } from '@mui/material'
import { Add as AddIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useFhirResourceTypes } from '../../hooks/useFhirMetadata'
import { useBundleBuilder } from '../../contexts/BundleBuilderContext'
import { getResourceIcon } from './constants'
import { generateId } from '../../utils/validation'
import { TWCORE_RESOURCE_DEFAULTS } from '../../constants/twcoreResourceDefaults'

interface AddResourceButtonProps {
  onDirty: () => void
}

export default function AddResourceButton({ onDirty }: AddResourceButtonProps) {
  const { t } = useTranslation('measures')
  const { data: resourceTypes } = useFhirResourceTypes()
  const { state, dispatch } = useBundleBuilder()
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)

  const hasPatient = state.entries.some((e) => e.resourceType === 'Patient')

  const handleAdd = (resourceType: string) => {
    const id = resourceType.toLowerCase() + '-' + generateId().slice(0, 8)
    const defaults = TWCORE_RESOURCE_DEFAULTS[resourceType]
    dispatch({
      type: 'ADD_ENTRY',
      payload: {
        id,
        resourceType,
        resourceData: { id, ...defaults },
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
        {t('testCaseBuilder.addResource')}
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
              {getResourceIcon(type)}
            </ListItemIcon>
            <ListItemText>{type}</ListItemText>
          </MenuItem>
        ))}
      </Menu>
    </>
  )
}

import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Typography,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Chip,
  Stack,
  Collapse,
  Alert,
  CircularProgress,
  Divider,
} from '@mui/material'
import {
  AccountTree as TreeIcon,
  ArrowUpward as DependentIcon,
  ArrowDownward as DependencyIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  Warning as WarningIcon,
} from '@mui/icons-material'
import { useLibraryDependencies, useLibraryDependents } from '../../hooks/useCql'
import type { CqlLibrary } from '../../types'

interface LibraryDependencyPanelProps {
  libraryId: string | null
  libraryName: string | null
  onLoadLibrary?: (lib: CqlLibrary) => void
}

export default function LibraryDependencyPanel({
  libraryId,
  libraryName,
  onLoadLibrary,
}: LibraryDependencyPanelProps) {
  const { t } = useTranslation('editor')
  const [depsOpen, setDepsOpen] = useState(true)
  const [dependentsOpen, setDependentsOpen] = useState(true)

  const { data: dependencies = [], isLoading: depsLoading } = useLibraryDependencies(libraryId)
  const { data: dependents = [], isLoading: dependentsLoading } = useLibraryDependents(libraryName)

  if (!libraryId || !libraryName) {
    return (
      <Box sx={{ p: 2 }}>
        <Typography variant="body2" color="text.secondary">
          {t('dependency.saveFirst')}
        </Typography>
      </Box>
    )
  }

  return (
    <Box sx={{ p: 0 }}>
      <Box
        sx={{
          p: 1.5,
          background: 'linear-gradient(135deg, rgba(13,115,119,0.06) 0%, rgba(20,163,168,0.03) 100%)',
          borderBottom: '1px solid',
          borderColor: 'rgba(13,115,119,0.1)',
        }}
      >
        <Stack direction="row" alignItems="center" spacing={1}>
          <TreeIcon sx={{ fontSize: 18, color: 'primary.main' }} />
          <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
            {t('dependency.title')}
          </Typography>
        </Stack>
      </Box>

      {/* Dependencies (what this library uses) */}
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        sx={{ px: 1.5, py: 0.75, cursor: 'pointer' }}
        onClick={() => setDepsOpen(!depsOpen)}
      >
        <Stack direction="row" alignItems="center" spacing={0.5}>
          <DependencyIcon sx={{ fontSize: 16, color: 'info.main' }} />
          <Typography variant="subtitle2">
            {t('dependency.dependencies')}
          </Typography>
          <Chip label={dependencies.length} size="small" sx={{ height: 18, fontSize: '0.7rem' }} />
        </Stack>
        {depsOpen ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
      </Stack>
      <Collapse in={depsOpen}>
        {depsLoading ? (
          <Box sx={{ p: 2, textAlign: 'center' }}>
            <CircularProgress size={20} />
          </Box>
        ) : dependencies.length === 0 ? (
          <Typography variant="caption" color="text.secondary" sx={{ px: 2, pb: 1, display: 'block' }}>
            {t('dependency.noDependencies')}
          </Typography>
        ) : (
          <List dense disablePadding>
            {dependencies.map((dep) => (
              <ListItem
                key={dep.id}
                component="div"
                sx={{
                  py: 0.25,
                  px: 2,
                  cursor: onLoadLibrary ? 'pointer' : 'default',
                  '&:hover': onLoadLibrary ? { bgcolor: 'action.hover' } : {},
                }}
                onClick={() => onLoadLibrary?.(dep)}
              >
                <ListItemIcon sx={{ minWidth: 28 }}>
                  <DependencyIcon sx={{ fontSize: 14, color: 'info.main' }} />
                </ListItemIcon>
                <ListItemText
                  primary={dep.name}
                  secondary={`v${dep.version}`}
                  primaryTypographyProps={{ variant: 'body2', noWrap: true }}
                  secondaryTypographyProps={{ variant: 'caption' }}
                />
                <Chip
                  label={dep.status}
                  size="small"
                  color={dep.status === 'active' ? 'success' : dep.status === 'draft' ? 'warning' : 'default'}
                  sx={{ height: 18, fontSize: '0.65rem' }}
                />
              </ListItem>
            ))}
          </List>
        )}
      </Collapse>

      <Divider />

      {/* Dependents (impact analysis - what uses this library) */}
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        sx={{ px: 1.5, py: 0.75, cursor: 'pointer' }}
        onClick={() => setDependentsOpen(!dependentsOpen)}
      >
        <Stack direction="row" alignItems="center" spacing={0.5}>
          <DependentIcon sx={{ fontSize: 16, color: 'warning.main' }} />
          <Typography variant="subtitle2">
            {t('dependency.impactAnalysis')}
          </Typography>
          <Chip label={dependents.length} size="small" sx={{ height: 18, fontSize: '0.7rem' }} />
        </Stack>
        {dependentsOpen ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
      </Stack>
      <Collapse in={dependentsOpen}>
        {dependentsLoading ? (
          <Box sx={{ p: 2, textAlign: 'center' }}>
            <CircularProgress size={20} />
          </Box>
        ) : dependents.length === 0 ? (
          <Typography variant="caption" color="text.secondary" sx={{ px: 2, pb: 1, display: 'block' }}>
            {t('dependency.noDependents')}
          </Typography>
        ) : (
          <>
            <Alert severity="info" sx={{ mx: 1, mb: 0.5, py: 0 }}>
              <Typography variant="caption">
                {t('dependency.affectedLibraries', { count: dependents.length })}
              </Typography>
            </Alert>
            <List dense disablePadding>
              {dependents.map((dep) => (
                <ListItem
                  key={dep.id}
                  component="div"
                  sx={{
                    py: 0.25,
                    px: 2,
                    cursor: onLoadLibrary ? 'pointer' : 'default',
                    '&:hover': onLoadLibrary ? { bgcolor: 'action.hover' } : {},
                  }}
                  onClick={() => onLoadLibrary?.(dep)}
                >
                  <ListItemIcon sx={{ minWidth: 28 }}>
                    <WarningIcon sx={{ fontSize: 14, color: 'warning.main' }} />
                  </ListItemIcon>
                  <ListItemText
                    primary={dep.name}
                    secondary={`v${dep.version}`}
                    primaryTypographyProps={{ variant: 'body2', noWrap: true }}
                    secondaryTypographyProps={{ variant: 'caption' }}
                  />
                  <Chip
                    label={dep.status}
                    size="small"
                    color={dep.status === 'active' ? 'success' : dep.status === 'draft' ? 'warning' : 'default'}
                    sx={{ height: 18, fontSize: '0.65rem' }}
                  />
                </ListItem>
              ))}
            </List>
          </>
        )}
      </Collapse>
    </Box>
  )
}

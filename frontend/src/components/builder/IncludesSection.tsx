import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  Button,
  Typography,
  Autocomplete,
} from '@mui/material'
import { alpha } from '@mui/material/styles'
import { Add as AddIcon } from '@mui/icons-material'
import ElementListItem from './ElementListItem'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import SnippetPreview from './SnippetPreview'
import { useRepositoryLibraries } from '../../hooks/useCql'
import { cqlApi } from '../../api'

interface IncludesSectionProps {
  includes: string[]
  onInsert: (cqlSnippet: string) => void
  onDelete?: (identifier: string) => void
  onGoTo?: (identifier: string) => void
  onEdit?: (identifier: string, newSnippet: string) => void
}

interface LibraryOption {
  name: string
  version: string
}

/**
 * Parse an include string like:
 *   FHIRHelpers version '4.0.1'
 *   "CDS_Connect" version '1.0' called C3F
 */
function parseInclude(raw: string): { name: string; version: string; alias: string } | null {
  // Try quoted name first: include "Lib Name" version '1.0' called Alias
  const quoted = raw.match(/^"([^"]+)"\s+version\s+'([^']+)'(?:\s+called\s+(\S+))?/)
  if (quoted) return { name: quoted[1], version: quoted[2], alias: quoted[3] || '' }
  // Unquoted: include FHIRHelpers version '4.0.1'
  const unquoted = raw.match(/^(\S+)\s+version\s+'([^']+)'(?:\s+called\s+(\S+))?/)
  if (unquoted) return { name: unquoted[1], version: unquoted[2], alias: unquoted[3] || '' }
  return null
}

function getIncludeIdentifier(raw: string): string {
  const parsed = parseInclude(raw)
  if (parsed?.alias) return parsed.alias
  if (parsed) return parsed.name
  // Fallback: first word
  return raw.split(/\s/)[0] || raw
}

export default function IncludesSection({ includes, onInsert, onDelete, onGoTo, onEdit }: IncludesSectionProps) {
  const { t } = useTranslation('builder')
  const { data: repoLibraries = [] } = useRepositoryLibraries()
  const [showForm, setShowForm] = useState(false)
  const [selectedLib, setSelectedLib] = useState<LibraryOption | null>(null)
  const [versions, setVersions] = useState<string[]>([])
  const [selectedVersion, setSelectedVersion] = useState('')
  const [alias, setAlias] = useState('')
  const [loadingVersions, setLoadingVersions] = useState(false)
  const [editingItem, setEditingItem] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')

  const uniqueLibs: LibraryOption[] = repoLibraries.map((r) => ({
    name: r.name,
    version: r.version,
  }))

  const handleSelectLibrary = async (lib: LibraryOption | null) => {
    setSelectedLib(lib)
    setSelectedVersion('')
    setVersions([])
    if (lib) {
      setAlias(lib.name.replace(/\s+/g, ''))
      setLoadingVersions(true)
      try {
        const versionList = await cqlApi.getLibraryVersions(lib.name)
        setVersions(versionList.map((v) => v.version))
        if (versionList.length > 0) {
          setSelectedVersion(versionList[0].version)
        }
      } catch {
        setVersions([lib.version])
        setSelectedVersion(lib.version)
      } finally {
        setLoadingVersions(false)
      }
    }
  }

  const handleAdd = () => {
    if (!selectedLib || !selectedVersion) return
    const snippet = `include "${selectedLib.name}" version '${selectedVersion}' called ${alias}`
    setPreviewSnippet(snippet)
  }

  const handleConfirmInsert = () => {
    if (editingItem) {
      onEdit?.(editingItem, previewSnippet)
      setEditingItem(null)
    } else {
      onInsert(previewSnippet)
    }
    resetForm()
  }

  const handleStartEdit = (raw: string) => {
    const parsed = parseInclude(raw)
    if (!parsed) return
    const id = getIncludeIdentifier(raw)
    setEditingItem(id)
    setSelectedLib({ name: parsed.name, version: parsed.version })
    setSelectedVersion(parsed.version)
    setVersions([parsed.version])
    setAlias(parsed.alias || parsed.name)
    setShowForm(true)
  }

  const resetForm = () => {
    setShowForm(false)
    setSelectedLib(null)
    setSelectedVersion('')
    setAlias('')
    setEditingItem(null)
    setPreviewSnippet('')
  }

  const handleConfirmDelete = () => {
    if (deleteTarget) {
      onDelete?.(deleteTarget)
      setDeleteTarget(null)
    }
  }

  return (
    <Stack spacing={0.5}>
      {includes.length > 0 ? (
        includes.map((inc, idx) => {
          const id = getIncludeIdentifier(inc)
          return (
            <ElementListItem
              key={idx}
              label={inc}
              onGoTo={() => onGoTo?.(id)}
              onEdit={() => handleStartEdit(inc)}
              onDelete={() => setDeleteTarget(id)}
            />
          )
        })
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          {t('common.noItemsFound', { type: t('sections.includes').toLowerCase() })}
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          {t('common.addItem', { type: 'Include' })}
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: (theme) => alpha(theme.palette.primary.main, 0.03), borderRadius: 1 }}>
          <Autocomplete
            size="small"
            options={uniqueLibs}
            getOptionLabel={(o) => `${o.name} (${o.version})`}
            value={selectedLib}
            onChange={(_, val) => handleSelectLibrary(val)}
            renderInput={(params) => <TextField {...params} label={t('includes.library')} placeholder={t('includes.searchLibraries')} />}
          />
          {selectedLib && (
            <>
              <TextField
                select
                size="small"
                label={t('includes.version')}
                value={selectedVersion}
                onChange={(e) => setSelectedVersion(e.target.value)}
                disabled={loadingVersions}
                SelectProps={{ native: true }}
              >
                <option value="" />
                {versions.map((v) => (
                  <option key={v} value={v}>{v}</option>
                ))}
              </TextField>
              <TextField
                size="small"
                label={t('includes.alias')}
                value={alias}
                onChange={(e) => setAlias(e.target.value)}
              />
              {previewSnippet ? (
                <SnippetPreview
                  snippet={previewSnippet}
                  onInsert={handleConfirmInsert}
                  onCancel={() => setPreviewSnippet('')}
                  insertLabel={editingItem ? t('common.update') : t('common.insert')}
                />
              ) : (
                <Stack direction="row" spacing={1}>
                  <Button size="small" variant="outlined" onClick={handleAdd} disabled={!selectedVersion || !alias}>
                    {editingItem ? t('common.previewUpdate') : t('common.previewInsert')}
                  </Button>
                  <Button size="small" onClick={resetForm}>{t('common.cancel')}</Button>
                </Stack>
              )}
            </>
          )}
        </Stack>
      )}

      <ConfirmDeleteDialog
        open={!!deleteTarget}
        title={t('common.deleteElement')}
        itemName={deleteTarget || ''}
        message={t('common.deleteConfirm', { name: deleteTarget })}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
      />
    </Stack>
  )
}

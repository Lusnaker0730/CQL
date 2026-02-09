import { useState } from 'react'
import {
  Stack,
  TextField,
  Button,
  Typography,
  Autocomplete,
  List,
  ListItem,
  ListItemText,
} from '@mui/material'
import { Add as AddIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useLibrariesMetadata } from '../../hooks/useCql'
import { cqlApi } from '../../api'

interface IncludesSectionProps {
  includes: string[]
  onInsert: (cqlSnippet: string) => void
}

interface LibraryOption {
  name: string
  version: string
}

export default function IncludesSection({ includes, onInsert }: IncludesSectionProps) {
  const { data: metadata = [] } = useLibrariesMetadata()
  const [showForm, setShowForm] = useState(false)
  const [selectedLib, setSelectedLib] = useState<LibraryOption | null>(null)
  const [versions, setVersions] = useState<string[]>([])
  const [selectedVersion, setSelectedVersion] = useState('')
  const [alias, setAlias] = useState('')
  const [loadingVersions, setLoadingVersions] = useState(false)

  const libraryOptions: LibraryOption[] = metadata.map((m) => ({
    name: m.name,
    version: m.version,
  }))

  // Deduplicate by name
  const uniqueLibs = libraryOptions.filter(
    (lib, idx, arr) => arr.findIndex((l) => l.name === lib.name) === idx
  )

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
        // Use the known version
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
    onInsert(snippet)
    setShowForm(false)
    setSelectedLib(null)
    setSelectedVersion('')
    setAlias('')
  }

  return (
    <Stack spacing={1}>
      {includes.length > 0 ? (
        <List dense disablePadding>
          {includes.map((inc, idx) => (
            <ListItem key={idx} disablePadding sx={{ py: 0.25 }}>
              <ListItemText
                primary={
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                    {inc}
                  </Typography>
                }
              />
            </ListItem>
          ))}
        </List>
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          No includes found
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          Add Include
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: 'rgba(13,115,119,0.03)', borderRadius: 1 }}>
          <Autocomplete
            size="small"
            options={uniqueLibs}
            getOptionLabel={(o) => `${o.name} (${o.version})`}
            value={selectedLib}
            onChange={(_, val) => handleSelectLibrary(val)}
            renderInput={(params) => <TextField {...params} label="Library" placeholder="Search libraries..." />}
          />
          {selectedLib && (
            <>
              <TextField
                select
                size="small"
                label="Version"
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
                label="Alias"
                value={alias}
                onChange={(e) => setAlias(e.target.value)}
              />
              <Stack direction="row" spacing={1}>
                <GradientButton onClick={handleAdd} disabled={!selectedVersion || !alias}>
                  Insert
                </GradientButton>
                <Button size="small" onClick={() => setShowForm(false)}>Cancel</Button>
              </Stack>
            </>
          )}
        </Stack>
      )}
    </Stack>
  )
}

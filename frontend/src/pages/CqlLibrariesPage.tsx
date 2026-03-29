import { useState } from 'react'
import { Box, Skeleton, Stack } from '@mui/material'
import { useCqlLibrary } from '../hooks/useCqlLibraries'
import CqlLibraryList from '../components/cql-libraries/CqlLibraryList'
import CreateLibraryDialog from '../components/cql-libraries/CreateLibraryDialog'
import CqlLibraryWorkspace from '../components/cql-libraries/CqlLibraryWorkspace'

export default function CqlLibrariesPage() {
  const [selectedId, setSelectedId] = useState<string | undefined>(undefined)
  const [createOpen, setCreateOpen] = useState(false)

  const { data: fullLibrary, isLoading: loadingLibrary } = useCqlLibrary(selectedId)

  // Show workspace when a library is selected
  if (selectedId) {
    if (loadingLibrary) {
      return (
        <Box sx={{ p: 3 }}>
          <Stack spacing={2}>
            <Skeleton variant="rectangular" height={60} />
            <Skeleton variant="rectangular" height={400} />
          </Stack>
        </Box>
      )
    }

    if (fullLibrary) {
      return (
        <CqlLibraryWorkspace
          library={fullLibrary}
          onBack={() => setSelectedId(undefined)}
        />
      )
    }
  }

  // Show list
  return (
    <>
      <CqlLibraryList
        onSelect={setSelectedId}
        onCreate={() => setCreateOpen(true)}
      />
      <CreateLibraryDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(lib) => setSelectedId(lib.id)}
      />
    </>
  )
}

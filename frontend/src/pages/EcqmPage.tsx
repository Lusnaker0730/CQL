import { useState } from 'react'
import { Box, Skeleton, Stack } from '@mui/material'
import {
  useEcqmArtifacts, useEcqmArtifact, useCreateEcqmArtifact,
  useDeleteEcqmArtifact, useDuplicateEcqmArtifact,
} from '../hooks/useEcqm'
import EcqmArtifactList from '../components/ecqm/EcqmArtifactList'
import EcqmArtifactModal from '../components/ecqm/EcqmArtifactModal'
import EcqmArtifactWorkspace from '../components/ecqm/EcqmArtifactWorkspace'

export default function EcqmPage() {
  const [selectedId, setSelectedId] = useState<number | undefined>(undefined)
  const [createOpen, setCreateOpen] = useState(false)

  const { data: artifacts = [] } = useEcqmArtifacts()
  const { data: fullArtifact, isLoading: loadingArtifact } = useEcqmArtifact(selectedId)
  const createMutation = useCreateEcqmArtifact()
  const deleteMutation = useDeleteEcqmArtifact()
  const duplicateMutation = useDuplicateEcqmArtifact()

  const handleCreate = (data: { name: string; scoringType: string; populationBasis: string; description?: string }) => {
    createMutation.mutate(data, {
      onSuccess: (created) => {
        setCreateOpen(false)
        setSelectedId(created.id)
      },
    })
  }

  const handleDelete = (id: number) => {
    deleteMutation.mutate(id, {
      onSuccess: () => {
        if (selectedId === id) setSelectedId(undefined)
      },
    })
  }

  const handleDuplicate = (id: number) => {
    duplicateMutation.mutate(id)
  }

  // Show workspace when an artifact is selected
  if (selectedId) {
    if (loadingArtifact) {
      return (
        <Box sx={{ p: 3 }}>
          <Stack spacing={2}>
            <Skeleton variant="rectangular" height={60} />
            <Skeleton variant="rectangular" height={400} />
          </Stack>
        </Box>
      )
    }

    if (fullArtifact) {
      return (
        <EcqmArtifactWorkspace
          artifact={fullArtifact}
          onBack={() => setSelectedId(undefined)}
          onArtifactUpdate={() => { /* React Query invalidation handles cache refresh */ }}
        />
      )
    }
  }

  // Show list
  return (
    <>
      <EcqmArtifactList
        artifacts={artifacts}
        onSelect={setSelectedId}
        onCreate={() => setCreateOpen(true)}
        onDuplicate={handleDuplicate}
        onDelete={handleDelete}
      />
      <EcqmArtifactModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onSubmit={handleCreate}
      />
    </>
  )
}

import { useState } from 'react'
import { Box, Typography, Dialog, DialogTitle, DialogContent, DialogActions, Button } from '@mui/material'
import ArtifactList from '../components/authoring/ArtifactList'
import ArtifactModal from '../components/authoring/ArtifactModal'
import ArtifactWorkspace from '../components/authoring/ArtifactWorkspace'
import ImportCqlDialog from '../components/authoring/import/ImportCqlDialog'
import { useArtifacts, useArtifact, useCreateArtifact, useDeleteArtifact, useDuplicateArtifact } from '../hooks/useAuthoring'
import type { ArtifactSummary, Artifact, ArtifactRequest } from '../types/authoring'

export default function AuthoringPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null)

  const { data: artifacts = [], isLoading, error } = useArtifacts()
  const { data: fullArtifact } = useArtifact(selectedId ?? undefined)
  const createMutation = useCreateArtifact()
  const deleteMutation = useDeleteArtifact()
  const duplicateMutation = useDuplicateArtifact()

  const handleSelect = (artifact: ArtifactSummary) => {
    setSelectedId(artifact.id)
  }

  const handleCreate = (request: ArtifactRequest) => {
    createMutation.mutate(request, {
      onSuccess: (created) => {
        setCreateOpen(false)
        setSelectedId(created.id)
      },
    })
  }

  const handleDelete = () => {
    if (deleteTarget === null) return
    deleteMutation.mutate(deleteTarget, {
      onSuccess: () => {
        if (selectedId === deleteTarget) setSelectedId(null)
        setDeleteTarget(null)
      },
    })
  }

  const handleDuplicate = (id: number) => {
    duplicateMutation.mutate(id)
  }

  const handleBack = () => {
    setSelectedId(null)
  }

  const handleArtifactUpdate = (updated: Artifact) => {
    // artifact data refreshed via React Query
    void updated
  }

  return (
    <Box sx={{ height: 'calc(100vh - 120px)', p: 2, display: 'flex', flexDirection: 'column' }}>
      {!selectedId && (
        <>
          <Box sx={{ mb: 2 }}>
            <Typography variant="h5" sx={{ mb: 0.5 }}>
              CDS Authoring Tool
            </Typography>
            <Box
              sx={{
                width: 48,
                height: 3,
                borderRadius: 2,
                background: 'linear-gradient(90deg, #0D7377, #14A3A8)',
                mb: 1,
              }}
            />
            <Typography variant="body2" color="text.secondary">
              Build clinical decision support rules visually and generate CQL automatically.
            </Typography>
          </Box>

          <Box sx={{ flex: 1, minHeight: 0 }}>
            <ArtifactList
              artifacts={artifacts}
              loading={isLoading}
              error={error}
              onSelect={handleSelect}
              onCreate={() => setCreateOpen(true)}
              onImport={() => setImportOpen(true)}
              onDelete={(id) => setDeleteTarget(id)}
              onDuplicate={handleDuplicate}
            />
          </Box>

          <ArtifactModal
            open={createOpen}
            onClose={() => setCreateOpen(false)}
            onSubmit={handleCreate}
          />

          <ImportCqlDialog
            open={importOpen}
            onClose={() => setImportOpen(false)}
            onImported={(artifactId) => setSelectedId(artifactId)}
          />
        </>
      )}

      {selectedId && fullArtifact && (
        <Box sx={{ flex: 1, minHeight: 0 }}>
          <ArtifactWorkspace
            artifact={fullArtifact}
            onBack={handleBack}
            onArtifactUpdate={handleArtifactUpdate}
          />
        </Box>
      )}

      {/* Delete confirmation dialog */}
      <Dialog open={deleteTarget !== null} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>Delete Artifact</DialogTitle>
        <DialogContent>
          Are you sure you want to delete this artifact? This action cannot be undone.
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>Cancel</Button>
          <Button onClick={handleDelete} color="error" variant="contained">
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

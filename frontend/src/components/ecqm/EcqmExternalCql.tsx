import { useEcqmExternalCqlList, useUploadEcqmExternalCql, useDeleteEcqmExternalCql } from '../../hooks/useEcqm'
import { ExternalCqlView } from '../authoring/external-cql/ExternalCql'
import type { ExternalCqlLibrary } from '../../types/authoring'

interface Props {
  artifactId: number
  onApplyToArtifact?: (lib: ExternalCqlLibrary) => void
}

export default function EcqmExternalCql({ artifactId, onApplyToArtifact }: Props) {
  const { data: libraries = [], isLoading, error } = useEcqmExternalCqlList(artifactId)
  const uploadMutation = useUploadEcqmExternalCql(artifactId)
  const deleteMutation = useDeleteEcqmExternalCql(artifactId)

  return (
    <ExternalCqlView
      libraries={libraries}
      isLoading={isLoading}
      error={error}
      uploadMutation={uploadMutation}
      deleteMutation={deleteMutation}
      onApplyToArtifact={onApplyToArtifact}
    />
  )
}

import { useEffect, useRef } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { versionApi, type VersionInfo } from '../../api'
import { useNotification } from '../../hooks/useNotification'

/**
 * Polls the backend's {@code /api/version} endpoint and busts the React Query cache
 * when the backend identity changes (new deploy, restart, etc).
 *
 * <p>Rationale: before this, many hooks (e.g. {@code useModifiers}) had
 * {@code staleTime: Infinity} to avoid re-fetching catalogs. That optimization became
 * a footgun after the #230 refactor changed {@code ModifierDefinition}'s wire shape
 * — users with open tabs kept the old schema indefinitely, silently rendering broken
 * UI states referencing removed fields. This provider restores the "restart = client
 * sees new stuff" invariant without us having to lower every hook's staleTime.
 *
 * <p>The signal we poll is the backend startup timestamp (primary) + commit SHA
 * (auxiliary). Startup time is the conservative choice: any restart — whether a
 * code change, a config change, a crash-loop recovery — busts the cache. This is
 * intentionally broader than "only on code change" to avoid hot-swapped jar
 * edge cases the SHA can't see.
 */
const VERSION_POLL_INTERVAL_MS = 5 * 60 * 1000 // 5 min — same order as STALE_5M

interface Props {
  children: React.ReactNode
}

function keyOf(v: VersionInfo | undefined): string | null {
  if (!v) return null
  return `${v.startupTime}|${v.commitSha}`
}

export default function VersionCheckProvider({ children }: Props) {
  const queryClient = useQueryClient()
  const { showNotification } = useNotification()
  const { t } = useTranslation('common')

  const { data } = useQuery({
    queryKey: ['app', 'version'],
    queryFn: versionApi.getVersion,
    staleTime: VERSION_POLL_INTERVAL_MS,
    refetchInterval: VERSION_POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    retry: 1,
  })

  // Snapshot of the first successful response — the "this is the version we mounted with"
  // anchor. All subsequent polls are compared against this.
  const initialKeyRef = useRef<string | null>(null)
  const hasNotifiedRef = useRef(false)

  useEffect(() => {
    const currentKey = keyOf(data)
    if (!currentKey) return

    if (initialKeyRef.current === null) {
      initialKeyRef.current = currentKey
      return
    }

    if (currentKey !== initialKeyRef.current && !hasNotifiedRef.current) {
      hasNotifiedRef.current = true
      // Invalidate all server-state queries so the next mount / focus fetches fresh.
      // Don't force-reload — user may have unsaved work. They'll see up-to-date data
      // as they navigate, and the toast tells them a refresh gets the new UI.
      queryClient.invalidateQueries()
      showNotification(t('versionCheck.newVersionAvailable'), 'info', 10000)
    }
  }, [data, queryClient, showNotification, t])

  return <>{children}</>
}

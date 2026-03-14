import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useNotification } from './useNotification'

/**
 * Returns a function that copies text to the clipboard and shows a notification.
 */
export function useCopyToClipboard() {
  const { t } = useTranslation('builder')
  const { showNotification } = useNotification()

  return useCallback(
    async (text: string) => {
      try {
        await navigator.clipboard.writeText(text)
        showNotification(t('common.copiedToClipboard'), 'success', 2000)
      } catch {
        showNotification(t('common.copyFailed'), 'error', 2000)
      }
    },
    [showNotification, t]
  )
}

import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Tabs,
  Tab,
  Stack,
} from '@mui/material'
import { Close as CloseIcon } from '@mui/icons-material'
import { useTerminologyDrawer } from '../../hooks/useTerminologyDrawer'
import { APP_BAR_HEIGHT } from '../../constants/layout'
import DrawerCodeSearchPanel from './DrawerCodeSearchPanel'
import DrawerValueSetPanel from './DrawerValueSetPanel'
import DrawerCodeLookupPanel from './DrawerCodeLookupPanel'

const DRAWER_WIDTH = 420

export default function TerminologyLookupDrawer() {
  const { t } = useTranslation('terminology')
  const { isOpen, options, closeDrawer } = useTerminologyDrawer()
  const [tab, setTab] = useState(0)

  // Sync tab from options when drawer opens
  useEffect(() => {
    if (isOpen && options.tab !== undefined) {
      setTab(options.tab)
    }
  }, [isOpen, options.tab])

  return (
    <Drawer
      anchor="right"
      open={isOpen}
      onClose={closeDrawer}
      variant="persistent"
      sx={{
        '& .MuiDrawer-paper': {
          width: DRAWER_WIDTH,
          boxSizing: 'border-box',
          top: APP_BAR_HEIGHT,
          height: `calc(100% - ${APP_BAR_HEIGHT}px)`,
        },
      }}
    >
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Header */}
        <Stack
          direction="row"
          justifyContent="space-between"
          alignItems="center"
          sx={{ px: 2, py: 1, borderBottom: 1, borderColor: 'divider' }}
        >
          <Typography variant="subtitle1" fontWeight={600}>
            {t('drawer.title')}
          </Typography>
          <IconButton size="small" onClick={closeDrawer}>
            <CloseIcon fontSize="small" />
          </IconButton>
        </Stack>

        {/* Tabs */}
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          variant="fullWidth"
          sx={{
            minHeight: 36,
            borderBottom: 1,
            borderColor: 'divider',
            '& .MuiTab-root': { minHeight: 36, textTransform: 'none', fontSize: '0.8rem' },
          }}
        >
          <Tab label={t('drawer.tabCodeSearch')} />
          <Tab label={t('drawer.tabValueSetBrowse')} />
          <Tab label={t('drawer.tabCodeLookup')} />
        </Tabs>

        {/* Panel content */}
        <Box sx={{ flex: 1, overflow: 'hidden', p: 1.5 }}>
          {tab === 0 && (
            <DrawerCodeSearchPanel
              initialSystem={options.system}
              initialSearchText={options.searchText}
              onSelect={options.onSelect}
            />
          )}
          {tab === 1 && (
            <DrawerValueSetPanel onSelect={options.onSelect} />
          )}
          {tab === 2 && (
            <DrawerCodeLookupPanel onSelect={options.onSelect} />
          )}
        </Box>
      </Box>
    </Drawer>
  )
}

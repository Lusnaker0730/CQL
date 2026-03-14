import { useState, useEffect, useCallback } from 'react'
import {
  Box,
  Container,
  Tabs,
  Tab,
  Button,
  Typography,
  IconButton,
  Menu,
  MenuItem,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import {
  LocalHospital as MedicalIcon,
  ArrowBack as BackIcon,
  Translate as TranslateIcon,
  MenuBook as IntroIcon,
  Hub as ConceptIcon,
  Public as TwcoreIcon,
  PlayCircle as ExampleIcon,
  RocketLaunch as StartIcon,
  Code as PlaygroundIcon,
  Quiz as QuizIcon,
  School as AdvancedIcon,
  BugReport as TroubleshootingIcon,
  ListAlt as CheatSheetIcon,
} from '@mui/icons-material'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import CqlIntroduction from '../components/learn/CqlIntroduction'
import ConceptGuide from '../components/learn/ConceptGuide'
import TwcoreGuide from '../components/learn/TwcoreGuide'
import InteractiveExamples from '../components/learn/InteractiveExamples'
import QuickStartGuide from '../components/learn/QuickStartGuide'
import AdvancedTopics from '../components/learn/AdvancedTopics'
import TroubleshootingGuide from '../components/learn/TroubleshootingGuide'
import CqlCheatSheet from '../components/learn/CqlCheatSheet'
import CqlPlayground from '../components/learn/CqlPlayground'
import CqlQuiz from '../components/learn/CqlQuiz'

const TAB_KEYS = [
  'introduction', 'concepts', 'twcore', 'examples', 'quickStart',
  'advanced', 'troubleshooting', 'cheatSheet',
  'playground', 'quiz',
] as const
const TAB_ICONS = [
  IntroIcon, ConceptIcon, TwcoreIcon, ExampleIcon, StartIcon,
  AdvancedIcon, TroubleshootingIcon, CheatSheetIcon,
  PlaygroundIcon, QuizIcon,
]

export default function LearnPage() {
  const { t, i18n } = useTranslation('landing')
  const { t: tc } = useTranslation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))
  const [langAnchor, setLangAnchor] = useState<null | HTMLElement>(null)

  const tabParam = searchParams.get('tab')
  const tabIndex = TAB_KEYS.indexOf(tabParam as typeof TAB_KEYS[number])
  const currentTab = tabIndex >= 0 ? tabIndex : 0

  const handleTabChange = useCallback((_: React.SyntheticEvent, newValue: number) => {
    setSearchParams({ tab: TAB_KEYS[newValue] })
  }, [setSearchParams])

  // Track learning progress in localStorage
  useEffect(() => {
    const key = `cql-learn-visited-${TAB_KEYS[currentTab]}`
    localStorage.setItem(key, 'true')
  }, [currentTab])

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      {/* Header */}
      <Box
        sx={{
          background: 'linear-gradient(135deg, #0D7377 0%, #1B3A5C 100%)',
          px: 3,
          py: 1.5,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Button
            startIcon={<BackIcon />}
            onClick={() => navigate('/login')}
            sx={{ color: 'rgba(255,255,255,0.9)', textTransform: 'none', mr: 2 }}
          >
            {t('learn.backToHome')}
          </Button>
          <MedicalIcon sx={{ color: 'white', fontSize: 22 }} />
          <Typography variant="subtitle1" fontWeight={700} sx={{ color: 'white' }}>
            {tc('app.title')}
          </Typography>
        </Box>
        <Box>
          <IconButton onClick={(e) => setLangAnchor(e.currentTarget)} sx={{ color: 'rgba(255,255,255,0.8)' }}>
            <TranslateIcon fontSize="small" />
          </IconButton>
          <Menu
            anchorEl={langAnchor}
            open={Boolean(langAnchor)}
            onClose={() => setLangAnchor(null)}
          >
            <MenuItem onClick={() => { i18n.changeLanguage('en'); setLangAnchor(null) }} selected={i18n.language === 'en'}>
              {tc('language.english')}
            </MenuItem>
            <MenuItem onClick={() => { i18n.changeLanguage('zh-TW'); setLangAnchor(null) }} selected={i18n.language === 'zh-TW'}>
              {tc('language.traditionalChinese')}
            </MenuItem>
          </Menu>
        </Box>
      </Box>

      {/* Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Container maxWidth="lg">
          <Tabs
            value={currentTab}
            onChange={handleTabChange}
            variant={isMobile ? 'scrollable' : 'scrollable'}
            scrollButtons="auto"
            sx={{
              '& .MuiTab-root': { textTransform: 'none', fontWeight: 600, minHeight: 52 },
            }}
          >
            {TAB_KEYS.map((key, i) => {
              const Icon = TAB_ICONS[i]
              return (
                <Tab
                  key={key}
                  icon={<Icon sx={{ fontSize: 20 }} />}
                  iconPosition="start"
                  label={t(`learn.nav.${key}`)}
                />
              )
            })}
          </Tabs>
        </Container>
      </Box>

      {/* Content */}
      <Box sx={{ flex: 1, py: 4 }}>
        <Container maxWidth="lg">
          {currentTab === 0 && <CqlIntroduction />}
          {currentTab === 1 && <ConceptGuide />}
          {currentTab === 2 && <TwcoreGuide />}
          {currentTab === 3 && <InteractiveExamples />}
          {currentTab === 4 && <QuickStartGuide />}
          {currentTab === 5 && <AdvancedTopics />}
          {currentTab === 6 && <TroubleshootingGuide />}
          {currentTab === 7 && <CqlCheatSheet />}
          {currentTab === 8 && <CqlPlayground />}
          {currentTab === 9 && <CqlQuiz />}
        </Container>
      </Box>

      {/* Footer */}
      <Box sx={{ py: 2, px: 3, borderTop: 1, borderColor: 'divider', textAlign: 'center' }}>
        <Box sx={{ display: 'flex', justifyContent: 'center', gap: 3 }}>
          {[
            { label: 'CQL Specification', href: 'https://cql.hl7.org/' },
            { label: 'HL7 FHIR', href: 'https://www.hl7.org/fhir/' },
            { label: 'TWCORE IG', href: 'https://twcore.mohw.gov.tw/ig/twcore/' },
          ].map((link) => (
            <Typography
              key={link.label}
              component="a"
              href={link.href}
              target="_blank"
              rel="noopener noreferrer"
              variant="caption"
              sx={{ color: 'text.secondary', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
            >
              {link.label}
            </Typography>
          ))}
        </Box>
      </Box>
    </Box>
  )
}

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { render } from '../../test/test-utils'
import EditorPage from '../EditorPage'

// Mock all child components
vi.mock('../../components/editor/CqlEditor', () => ({
  default: () => <div data-testid="cql-editor">CqlEditor</div>,
}))

vi.mock('../../components/editor/ElmViewer', () => ({
  default: () => <div data-testid="elm-viewer">ElmViewer</div>,
}))

vi.mock('../../components/execution/ExecutionPanel', () => ({
  default: () => <div data-testid="execution-panel">ExecutionPanel</div>,
}))

vi.mock('../../components/editor/LibraryQuickAccess', () => ({
  default: () => <div data-testid="library-quick-access">QuickAccess</div>,
}))

vi.mock('../../components/builder/CqlBuilderPanel', () => ({
  default: () => <div data-testid="cql-builder">CqlBuilder</div>,
}))

vi.mock('../../components/editor/LibraryDependencyPanel', () => ({
  default: () => <div data-testid="dependency-panel">Dependencies</div>,
}))

vi.mock('../../components/editor/LibraryShareDialog', () => ({
  default: () => null,
}))

vi.mock('../../components/common/HelpTooltip', () => ({
  default: () => null,
}))

vi.mock('../../components/editor/CreateVersionDialog', () => ({
  default: () => null,
}))

vi.mock('../../components/editor/VersionHistoryDialog', () => ({
  default: () => null,
}))

vi.mock('../../components/editor/VersionDiffDialog', () => ({
  default: () => null,
}))

vi.mock('../../components/common/TabPanel', () => {
  const TabPanel = ({ children, value, index }: { children: React.ReactNode; value: number; index: number }) =>
    value === index ? <div>{children}</div> : null
  TabPanel.displayName = 'TabPanel'
  return {
    default: TabPanel,
    a11yProps: (index: number, prefix: string) => ({
      id: `${prefix}-tab-${index}`,
      'aria-controls': `${prefix}-tabpanel-${index}`,
    }),
  }
})

vi.mock('../../hooks/useCql', () => ({
  useTranslate: () => ({ mutate: vi.fn(), isPending: false }),
  useCreateLibrary: () => ({ mutate: vi.fn(), isPending: false }),
  useExportLibrary: () => ({ mutate: vi.fn(), isPending: false }),
  useImportLibrary: () => ({ mutate: vi.fn(), isPending: false }),
  useLibrariesMetadata: () => ({ data: [] }),
  useLibrary: () => ({ data: null }),
}))

vi.mock('../../hooks/useTerminologyValidation', () => ({
  useTerminologyValidation: () => ({
    results: [],
    isValidating: false,
  }),
}))

vi.mock('../../hooks/useLibraryHistory', () => ({
  useLibraryHistory: () => ({
    addToRecent: vi.fn(),
    toggleFavorite: vi.fn(),
    isFavorite: () => false,
    recent: [],
    favorites: new Set(),
  }),
}))

vi.mock('../../hooks/useNotification', () => ({
  useNotification: () => ({
    showNotification: vi.fn(),
  }),
}))

vi.mock('../../constants/helpContent', () => ({
  helpContent: {
    editor: {
      translate: 'translate help',
      save: 'save help',
      export: 'export help',
      import: 'import help',
    },
  },
}))

describe('EditorPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should render editor title', () => {
    render(<EditorPage />)
    expect(screen.getByText('title')).toBeInTheDocument()
  })

  it('should render CqlEditor', () => {
    render(<EditorPage />)
    expect(screen.getByTestId('cql-editor')).toBeInTheDocument()
  })

  it('should render translate button', () => {
    render(<EditorPage />)
    expect(screen.getByText('toolbar.translate')).toBeInTheDocument()
  })

  it('should render save library button', () => {
    render(<EditorPage />)
    expect(screen.getByText('toolbar.saveLibrary')).toBeInTheDocument()
  })

  it('should render export button', () => {
    render(<EditorPage />)
    expect(screen.getByText('toolbar.export')).toBeInTheDocument()
  })

  it('should render import button', () => {
    render(<EditorPage />)
    expect(screen.getByText('toolbar.import')).toBeInTheDocument()
  })

  it('should render ELM/Errors tab by default', () => {
    render(<EditorPage />)
    expect(screen.getByText('tabs.elmErrors')).toBeInTheDocument()
  })

  it('should render Execute tab', () => {
    render(<EditorPage />)
    expect(screen.getByText('tabs.execute')).toBeInTheDocument()
  })

  it('should render Dependencies tab', () => {
    render(<EditorPage />)
    expect(screen.getByText('tabs.dependencies')).toBeInTheDocument()
  })

  it('should render builder toggle button', () => {
    render(<EditorPage />)
    expect(screen.getByText('toolbar.builder')).toBeInTheDocument()
  })
})

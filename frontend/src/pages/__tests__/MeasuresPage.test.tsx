import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test/test-utils'
import MeasuresPage from '../MeasuresPage'

vi.mock('../../api', () => ({
  measureApi: {
    getMeasure: vi.fn(),
    getDashboard: vi.fn().mockResolvedValue({
      totalMeasures: 0,
      byStatus: {},
      byScoring: {},
      recentEvaluations: [],
    }),
    getEnhancedDashboard: vi.fn().mockResolvedValue({}),
    getDashboardTrends: vi.fn().mockResolvedValue([]),
    getDashboardAlerts: vi.fn().mockResolvedValue([]),
    getQualityReport: vi.fn().mockResolvedValue(null),
  },
}))

vi.mock('../../components/measure/MeasureLibrary', () => ({
  default: () => <div data-testid="measure-library">MeasureLibrary</div>,
}))

vi.mock('../../components/measure/MeasureEditor', () => ({
  default: () => <div data-testid="measure-editor">MeasureEditor</div>,
}))

vi.mock('../../components/measure/MeasureComparison', () => ({
  default: () => <div data-testid="measure-comparison">MeasureComparison</div>,
}))

vi.mock('../../components/common/DashboardSkeleton', () => ({
  default: () => <div data-testid="dashboard-skeleton">Loading...</div>,
}))

vi.mock('../../components/common/StatusChip', () => ({
  default: ({ status }: { status: string }) => <span>{status}</span>,
}))

vi.mock('../../components/dashboard/DashboardFilterBar', () => ({
  default: () => <div data-testid="filter-bar">FilterBar</div>,
}))

vi.mock('../../components/dashboard/ScoreTrendChart', () => ({
  default: () => <div data-testid="score-trend">ScoreTrendChart</div>,
}))

vi.mock('../../components/dashboard/DepartmentDrilldownChart', () => ({
  default: () => <div data-testid="dept-chart">DeptChart</div>,
}))

vi.mock('../../components/dashboard/ScoreDistributionChart', () => ({
  default: () => <div data-testid="score-dist">ScoreDistChart</div>,
}))

vi.mock('../../components/dashboard/ThresholdAlertPanel', () => ({
  default: () => <div data-testid="alert-panel">AlertPanel</div>,
}))

vi.mock('../../components/dashboard/QualityReportPanel', () => ({
  default: () => <div data-testid="quality-report">QualityReport</div>,
}))

vi.mock('../../utils/scoreColors', () => ({
  getScoreChipColor: () => 'default',
}))

describe('MeasuresPage', () => {
  it('should render page title and subtitle', () => {
    render(<MeasuresPage />)
    expect(screen.getByText('page.title')).toBeInTheDocument()
    expect(screen.getByText('page.subtitle')).toBeInTheDocument()
  })

  it('should render three tabs', () => {
    render(<MeasuresPage />)
    expect(screen.getByText('page.tabs.dashboard')).toBeInTheDocument()
    expect(screen.getByText('page.tabs.measures')).toBeInTheDocument()
    expect(screen.getByText('page.tabs.comparison')).toBeInTheDocument()
  })

  it('should show dashboard by default', () => {
    render(<MeasuresPage />)
    // Dashboard tab is first, it renders MeasureDashboardPage which is not mocked
    // but dashboard API calls are mocked
    expect(screen.queryByTestId('measure-library')).not.toBeInTheDocument()
  })

  it('should switch to measures tab', async () => {
    const user = userEvent.setup()
    render(<MeasuresPage />)

    await user.click(screen.getByText('page.tabs.measures'))

    expect(screen.getByTestId('measure-library')).toBeInTheDocument()
  })

  it('should switch to comparison tab', async () => {
    const user = userEvent.setup()
    render(<MeasuresPage />)

    await user.click(screen.getByText('page.tabs.comparison'))

    expect(screen.getByTestId('measure-comparison')).toBeInTheDocument()
  })

  it('should switch between tabs', async () => {
    const user = userEvent.setup()
    render(<MeasuresPage />)

    await user.click(screen.getByText('page.tabs.measures'))
    expect(screen.getByTestId('measure-library')).toBeInTheDocument()

    await user.click(screen.getByText('page.tabs.comparison'))
    expect(screen.getByTestId('measure-comparison')).toBeInTheDocument()
    expect(screen.queryByTestId('measure-library')).not.toBeInTheDocument()
  })
})

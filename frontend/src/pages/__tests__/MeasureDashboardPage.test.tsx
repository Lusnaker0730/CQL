import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { render } from '../../test/test-utils'
import MeasureDashboardPage from '../MeasureDashboardPage'

vi.mock('../../api', () => ({
  measureApi: {
    getDashboard: vi.fn(),
    getEnhancedDashboard: vi.fn(),
    getDashboardTrends: vi.fn(),
    getDashboardAlerts: vi.fn(),
    getQualityReport: vi.fn(),
  },
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
  default: () => <div data-testid="score-trend">ScoreTrend</div>,
}))

vi.mock('../../components/dashboard/DepartmentDrilldownChart', () => ({
  default: () => <div data-testid="dept-chart">DeptChart</div>,
}))

vi.mock('../../components/dashboard/ScoreDistributionChart', () => ({
  default: () => <div data-testid="score-dist">ScoreDist</div>,
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

import { measureApi } from '../../api'

const mockDashboardData = {
  totalMeasures: 25,
  byStatus: { active: 10, draft: 8, retired: 5, 'in-review': 2 },
  byScoring: { proportion: 15, continuous: 10 },
  recentEvaluations: [
    { id: 1, measureName: 'BP Control', score: 85.5, status: 'active', createdAt: '2024-01-15' },
  ],
}

describe('MeasureDashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should show loading skeleton while data is loading', () => {
    vi.mocked(measureApi.getDashboard).mockReturnValue(new Promise(() => {}))
    vi.mocked(measureApi.getEnhancedDashboard).mockReturnValue(new Promise(() => {}))
    vi.mocked(measureApi.getDashboardTrends).mockReturnValue(new Promise(() => {}))
    vi.mocked(measureApi.getDashboardAlerts).mockReturnValue(new Promise(() => {}))
    vi.mocked(measureApi.getQualityReport).mockReturnValue(new Promise(() => {}))

    render(<MeasureDashboardPage />)

    expect(screen.getByTestId('dashboard-skeleton')).toBeInTheDocument()
  })

  it('should show error message on failure', async () => {
    vi.mocked(measureApi.getDashboard).mockRejectedValue(new Error('Network error'))
    vi.mocked(measureApi.getEnhancedDashboard).mockResolvedValue({})
    vi.mocked(measureApi.getDashboardTrends).mockResolvedValue([])
    vi.mocked(measureApi.getDashboardAlerts).mockResolvedValue([])
    vi.mocked(measureApi.getQualityReport).mockResolvedValue(null)

    render(<MeasureDashboardPage />)

    await waitFor(() => {
      expect(screen.getByText(/dashboard.loadError/)).toBeInTheDocument()
    })
  })

  it('should render dashboard title when data is loaded', async () => {
    vi.mocked(measureApi.getDashboard).mockResolvedValue(mockDashboardData)
    vi.mocked(measureApi.getEnhancedDashboard).mockResolvedValue({})
    vi.mocked(measureApi.getDashboardTrends).mockResolvedValue([])
    vi.mocked(measureApi.getDashboardAlerts).mockResolvedValue([])
    vi.mocked(measureApi.getQualityReport).mockResolvedValue(null)

    render(<MeasureDashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('dashboard.title')).toBeInTheDocument()
    })
  })

  it('should render filter bar when data is loaded', async () => {
    vi.mocked(measureApi.getDashboard).mockResolvedValue(mockDashboardData)
    vi.mocked(measureApi.getEnhancedDashboard).mockResolvedValue({})
    vi.mocked(measureApi.getDashboardTrends).mockResolvedValue([])
    vi.mocked(measureApi.getDashboardAlerts).mockResolvedValue([])
    vi.mocked(measureApi.getQualityReport).mockResolvedValue(null)

    render(<MeasureDashboardPage />)

    await waitFor(() => {
      expect(screen.getByTestId('filter-bar')).toBeInTheDocument()
    })
  })

  it('should render total measures count', async () => {
    vi.mocked(measureApi.getDashboard).mockResolvedValue(mockDashboardData)
    vi.mocked(measureApi.getEnhancedDashboard).mockResolvedValue({})
    vi.mocked(measureApi.getDashboardTrends).mockResolvedValue([])
    vi.mocked(measureApi.getDashboardAlerts).mockResolvedValue([])
    vi.mocked(measureApi.getQualityReport).mockResolvedValue(null)

    render(<MeasureDashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('25')).toBeInTheDocument()
    })
  })

  it('should render alert panel', async () => {
    vi.mocked(measureApi.getDashboard).mockResolvedValue(mockDashboardData)
    vi.mocked(measureApi.getEnhancedDashboard).mockResolvedValue({})
    vi.mocked(measureApi.getDashboardTrends).mockResolvedValue([])
    vi.mocked(measureApi.getDashboardAlerts).mockResolvedValue([])
    vi.mocked(measureApi.getQualityReport).mockResolvedValue(null)

    render(<MeasureDashboardPage />)

    await waitFor(() => {
      expect(screen.getByTestId('alert-panel')).toBeInTheDocument()
    })
  })

  it('should render score distribution chart', async () => {
    vi.mocked(measureApi.getDashboard).mockResolvedValue(mockDashboardData)
    vi.mocked(measureApi.getEnhancedDashboard).mockResolvedValue({})
    vi.mocked(measureApi.getDashboardTrends).mockResolvedValue([])
    vi.mocked(measureApi.getDashboardAlerts).mockResolvedValue([])
    vi.mocked(measureApi.getQualityReport).mockResolvedValue(null)

    render(<MeasureDashboardPage />)

    await waitFor(() => {
      expect(screen.getByTestId('score-dist')).toBeInTheDocument()
    })
  })

  it('should render recent evaluations table', async () => {
    vi.mocked(measureApi.getDashboard).mockResolvedValue(mockDashboardData)
    vi.mocked(measureApi.getEnhancedDashboard).mockResolvedValue({})
    vi.mocked(measureApi.getDashboardTrends).mockResolvedValue([])
    vi.mocked(measureApi.getDashboardAlerts).mockResolvedValue([])
    vi.mocked(measureApi.getQualityReport).mockResolvedValue(null)

    render(<MeasureDashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('BP Control')).toBeInTheDocument()
    })
  })

  it('should render quality report panel', async () => {
    vi.mocked(measureApi.getDashboard).mockResolvedValue(mockDashboardData)
    vi.mocked(measureApi.getEnhancedDashboard).mockResolvedValue({})
    vi.mocked(measureApi.getDashboardTrends).mockResolvedValue([])
    vi.mocked(measureApi.getDashboardAlerts).mockResolvedValue([])
    vi.mocked(measureApi.getQualityReport).mockResolvedValue(null)

    render(<MeasureDashboardPage />)

    await waitFor(() => {
      expect(screen.getByTestId('quality-report')).toBeInTheDocument()
    })
  })

  it('should show no data message when data is null', async () => {
    vi.mocked(measureApi.getDashboard).mockResolvedValue(null)
    vi.mocked(measureApi.getEnhancedDashboard).mockResolvedValue({})
    vi.mocked(measureApi.getDashboardTrends).mockResolvedValue([])
    vi.mocked(measureApi.getDashboardAlerts).mockResolvedValue([])
    vi.mocked(measureApi.getQualityReport).mockResolvedValue(null)

    render(<MeasureDashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('dashboard.noData')).toBeInTheDocument()
    })
  })
})

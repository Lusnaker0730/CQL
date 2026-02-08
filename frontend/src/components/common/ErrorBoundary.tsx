import { Component, type ReactNode, type ErrorInfo } from 'react'
import { Paper, Typography, Button, Stack, Box } from '@mui/material'
import { ErrorOutline as ErrorIcon, Refresh as RefreshIcon } from '@mui/icons-material'

interface ErrorBoundaryProps {
  children: ReactNode
  fallbackTitle?: string
}

interface ErrorBoundaryState {
  hasError: boolean
  error: Error | null
  errorInfo: ErrorInfo | null
}

export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false, error: null, errorInfo: null }
  }

  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    this.setState({ errorInfo })
    console.error('ErrorBoundary caught:', error, errorInfo)
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null, errorInfo: null })
  }

  render() {
    if (this.state.hasError) {
      return (
        <Paper
          sx={{
            p: 4,
            m: 2,
            textAlign: 'center',
            border: '1px solid',
            borderColor: 'error.light',
            bgcolor: 'rgba(211,47,47,0.04)',
          }}
        >
          <Stack spacing={2} alignItems="center">
            <ErrorIcon sx={{ fontSize: 48, color: 'error.main' }} />
            <Typography variant="h6" color="error.main">
              {this.props.fallbackTitle || 'Something went wrong'}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              An unexpected error occurred. Try refreshing this section.
            </Typography>
            <Button
              variant="contained"
              startIcon={<RefreshIcon />}
              onClick={this.handleReset}
              color="primary"
            >
              Try Again
            </Button>
            {import.meta.env.DEV && this.state.error && (
              <Box
                component="pre"
                sx={{
                  mt: 2,
                  p: 2,
                  bgcolor: '#1a1a1a',
                  color: '#ff6b6b',
                  borderRadius: 1,
                  fontSize: '0.75rem',
                  textAlign: 'left',
                  overflow: 'auto',
                  maxHeight: 200,
                  width: '100%',
                  fontFamily: '"Consolas", monospace',
                }}
              >
                {this.state.error.toString()}
                {this.state.errorInfo?.componentStack}
              </Box>
            )}
          </Stack>
        </Paper>
      )
    }

    return this.props.children
  }
}

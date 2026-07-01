import { Component, type ReactNode, type ErrorInfo } from 'react'
import { Paper, Typography, Button, Stack, Box } from '@mui/material'
import { alpha } from '@mui/material/styles'
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection.
import ErrorIcon from '@mui/icons-material/ErrorOutlined'
import RefreshIcon from '@mui/icons-material/Refresh'
import { Translation } from 'react-i18next'

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
        <Translation>
          {(t) => (
        <Paper
          sx={(theme) => ({
            p: 4,
            m: 2,
            textAlign: 'center',
            border: '1px solid',
            borderColor: 'error.light',
            bgcolor: alpha(theme.palette.error.main, 0.04),
          })}
        >
          <Stack spacing={2} sx={{
            alignItems: "center"
          }}>
            <ErrorIcon sx={{ fontSize: 48, color: 'error.main' }} />
            <Typography variant="h6" sx={{
              color: "error.main"
            }}>
              {this.props.fallbackTitle || t('errors.somethingWentWrong')}
            </Typography>
            <Typography variant="body2" sx={{
              color: "text.secondary"
            }}>
              {t('errors.unexpectedError')}
            </Typography>
            <Button
              variant="contained"
              startIcon={<RefreshIcon />}
              onClick={this.handleReset}
              color="primary"
            >
              {t('actions.tryAgain')}
            </Button>
            {import.meta.env.DEV && this.state.error && (
              <Box
                component="pre"
                sx={(theme) => ({
                  mt: 2,
                  p: 2,
                  bgcolor: theme.palette.mode === 'dark'
                    ? theme.palette.grey[900]
                    : theme.palette.grey[900],
                  color: theme.palette.error.light,
                  borderRadius: 1,
                  fontSize: '0.75rem',
                  textAlign: 'left',
                  overflow: 'auto',
                  maxHeight: 200,
                  width: '100%',
                  fontFamily: '"Consolas", monospace',
                })}
              >
                {this.state.error.toString()}
                {this.state.errorInfo?.componentStack}
              </Box>
            )}
          </Stack>
        </Paper>
          )}
        </Translation>
      );
    }

    return this.props.children
  }
}

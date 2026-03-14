import { useState, useCallback, useMemo } from 'react'
import {
  Box,
  Typography,
  Paper,
  Button,
  RadioGroup,
  FormControlLabel,
  Radio,
  Alert,
  LinearProgress,
  Chip,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'

const QUESTION_COUNT = 10

// Topic mapping: question index -> topic key
const TOPIC_MAP: Record<number, { key: string; tab: string }> = {
  0: { key: 'terminology', tab: 'concepts' },
  1: { key: 'terminology', tab: 'concepts' },
  2: { key: 'syntax', tab: 'concepts' },
  3: { key: 'concepts', tab: 'concepts' },
  4: { key: 'twcore', tab: 'twcore' },
  5: { key: 'concepts', tab: 'concepts' },
  6: { key: 'twcore', tab: 'twcore' },
  7: { key: 'syntax', tab: 'concepts' },
  8: { key: 'standards', tab: 'twcore' },
  9: { key: 'standards', tab: 'introduction' },
}

export default function CqlQuiz() {
  const { t } = useTranslation('landing')
  const [, setSearchParams] = useSearchParams()
  const [currentQ, setCurrentQ] = useState(0)
  const [selectedAnswer, setSelectedAnswer] = useState<number | null>(null)
  const [checked, setChecked] = useState(false)
  const [scores, setScores] = useState<boolean[]>([])
  const [finished, setFinished] = useState(false)

  const question = useMemo(() => {
    const qKey = `q${currentQ + 1}` as const
    const options = t(`learn.quiz.${qKey}.options`, { returnObjects: true }) as string[]
    return {
      question: t(`learn.quiz.${qKey}.question`),
      options,
      answer: t(`learn.quiz.${qKey}.answer`, { returnObjects: true }) as unknown as number,
      explanation: t(`learn.quiz.${qKey}.explanation`),
    }
  }, [currentQ, t])

  const handleCheck = useCallback(() => {
    if (selectedAnswer === null) return
    setChecked(true)
    setScores((prev) => [...prev, selectedAnswer === question.answer])
  }, [selectedAnswer, question.answer])

  const handleNext = useCallback(() => {
    if (currentQ < QUESTION_COUNT - 1) {
      setCurrentQ((prev) => prev + 1)
      setSelectedAnswer(null)
      setChecked(false)
    } else {
      setFinished(true)
    }
  }, [currentQ])

  const handleRestart = useCallback(() => {
    setCurrentQ(0)
    setSelectedAnswer(null)
    setChecked(false)
    setScores([])
    setFinished(false)
  }, [])

  const handleNavigateToTab = useCallback((tab: string) => {
    setSearchParams({ tab })
  }, [setSearchParams])

  const totalCorrect = scores.filter(Boolean).length

  // Compute missed topics from wrong answers
  const missedTopics = useMemo(() => {
    if (!finished) return []
    const topicSet = new Map<string, string>()
    scores.forEach((correct, index) => {
      if (!correct) {
        const topic = TOPIC_MAP[index]
        if (topic && !topicSet.has(topic.key)) {
          topicSet.set(topic.key, topic.tab)
        }
      }
    })
    return Array.from(topicSet.entries()).map(([key, tab]) => ({ key, tab }))
  }, [finished, scores])

  if (finished) {
    const ratio = totalCorrect / QUESTION_COUNT
    const feedbackKey = ratio === 1 ? 'perfect' : ratio >= 0.6 ? 'good' : 'needsWork'

    return (
      <Box>
        <Typography variant="h4" fontWeight={700} gutterBottom color="secondary.main">
          {t('learn.quiz.title')}
        </Typography>
        <Paper
          elevation={0}
          sx={{
            p: 4,
            borderRadius: 3,
            border: '1px solid',
            borderColor: 'divider',
            textAlign: 'center',
            maxWidth: 500,
            mx: 'auto',
            mt: 4,
          }}
        >
          <Typography variant="h3" fontWeight={800} color="primary.main" gutterBottom>
            {totalCorrect} / {QUESTION_COUNT}
          </Typography>
          <Typography variant="h6" gutterBottom>
            {t('learn.quiz.score', { score: totalCorrect, total: QUESTION_COUNT })}
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 3, lineHeight: 1.7 }}>
            {t(`learn.quiz.${feedbackKey}`)}
          </Typography>

          {missedTopics.length > 0 && (
            <Box sx={{ mb: 3 }}>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {t('learn.quiz.reviewTopics')}
              </Typography>
              <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center', flexWrap: 'wrap' }}>
                {missedTopics.map(({ key, tab }) => (
                  <Chip
                    key={key}
                    label={t(`learn.quiz.topics.${key}`)}
                    color="warning"
                    variant="outlined"
                    clickable
                    onClick={() => handleNavigateToTab(tab)}
                    sx={{ fontWeight: 600 }}
                  />
                ))}
              </Box>
            </Box>
          )}

          <Button variant="contained" onClick={handleRestart} sx={{ textTransform: 'none', px: 4 }}>
            {t('learn.quiz.restart')}
          </Button>
        </Paper>
      </Box>
    )
  }

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} gutterBottom color="secondary.main">
        {t('learn.quiz.title')}
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        {t('learn.quiz.subtitle')}
      </Typography>

      {/* Progress */}
      <Box sx={{ mb: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
          <Typography variant="caption" color="text.secondary">
            {t('learn.quiz.question', { current: currentQ + 1, total: QUESTION_COUNT })}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {Math.round(((currentQ + (checked ? 1 : 0)) / QUESTION_COUNT) * 100)}%
          </Typography>
        </Box>
        <LinearProgress
          variant="determinate"
          value={((currentQ + (checked ? 1 : 0)) / QUESTION_COUNT) * 100}
          sx={{ height: 6, borderRadius: 3 }}
        />
      </Box>

      <Paper
        elevation={0}
        sx={{
          p: 3,
          borderRadius: 3,
          border: '1px solid',
          borderColor: 'divider',
          maxWidth: 700,
        }}
      >
        <Typography variant="h6" fontWeight={700} sx={{ mb: 3 }}>
          {question.question}
        </Typography>

        <RadioGroup
          value={selectedAnswer}
          onChange={(_, val) => {
            if (!checked) setSelectedAnswer(Number(val))
          }}
        >
          {question.options.map((option: string, index: number) => {
            let bgcolor = 'transparent'
            if (checked) {
              if (index === question.answer) bgcolor = 'rgba(46,125,50,0.08)'
              else if (index === selectedAnswer && index !== question.answer) bgcolor = 'rgba(211,47,47,0.08)'
            }

            return (
              <FormControlLabel
                key={index}
                value={index}
                control={<Radio size="small" />}
                label={option}
                disabled={checked}
                sx={{
                  mb: 1,
                  p: 1,
                  mx: 0,
                  borderRadius: 2,
                  bgcolor,
                  border: '1px solid',
                  borderColor: checked && index === question.answer ? 'success.main' :
                    checked && index === selectedAnswer ? 'error.main' : 'transparent',
                  width: '100%',
                  '& .MuiFormControlLabel-label': { fontSize: '0.95rem' },
                }}
              />
            )
          })}
        </RadioGroup>

        {checked && (
          <Alert
            severity={selectedAnswer === question.answer ? 'success' : 'error'}
            sx={{ mt: 2, mb: 2 }}
          >
            <Typography variant="body2" fontWeight={600}>
              {selectedAnswer === question.answer ? t('learn.quiz.correct') : t('learn.quiz.incorrect')}
            </Typography>
            <Typography variant="body2" sx={{ mt: 0.5 }}>
              {question.explanation}
            </Typography>
          </Alert>
        )}

        <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mt: 2 }}>
          {!checked ? (
            <Button
              variant="contained"
              onClick={handleCheck}
              disabled={selectedAnswer === null}
              sx={{ textTransform: 'none' }}
            >
              {t('learn.quiz.checkAnswer')}
            </Button>
          ) : (
            <Button variant="contained" onClick={handleNext} sx={{ textTransform: 'none' }}>
              {currentQ < QUESTION_COUNT - 1 ? t('learn.quiz.next') : t('learn.quiz.score', { score: totalCorrect + (selectedAnswer === question.answer ? 1 : 0), total: QUESTION_COUNT })}
            </Button>
          )}
        </Box>
      </Paper>
    </Box>
  )
}

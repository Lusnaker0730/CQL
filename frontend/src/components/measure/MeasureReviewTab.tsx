import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  FormControlLabel,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import PersonAddIcon from '@mui/icons-material/PersonAddAlt1'
import RemoveIcon from '@mui/icons-material/PersonRemove'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import RateReviewIcon from '@mui/icons-material/RateReview'
import ReplyIcon from '@mui/icons-material/Reply'
import DoneIcon from '@mui/icons-material/TaskAlt'
import UndoIcon from '@mui/icons-material/Undo'
import EditIcon from '@mui/icons-material/Edit'
import type { MeasureDefinition, MeasureReviewComment, MeasureReviewer } from '../../types'
import { getStoredUsername } from '../../utils/validation'
import { extractApiError } from '../../utils/errorUtils'
import { MEASURE_STATUS } from '../../constants/measureConstants'
import {
  useAddReviewComment,
  useEditReviewComment,
  useMeasureReview,
  useResolveReviewComment,
  useReviewDecision,
  useSetReviewers,
} from '../../hooks/useMeasureReview'

interface MeasureReviewTabProps {
  measure: MeasureDefinition
}

/** The parts of a measure a comment thread can point at (MADiE's mainSection). */
const REVIEW_SECTIONS = ['general', 'cql', 'populations', 'testCases', 'metadata', 'terminology'] as const

const DECISION_COLOR: Record<string, 'default' | 'success' | 'error'> = {
  pending: 'default',
  approved: 'success',
  'changes-requested': 'error',
}

const formatTime = (iso?: string) => (iso ? new Date(iso).toLocaleString() : '')

/**
 * PAT-262 — the review of a measure: assigned reviewers and their decisions, the caller's own
 * decision while the measure is in review, and the comment threads (one level of replies,
 * anchored to a section of the measure, resolvable).
 */
export default function MeasureReviewTab({ measure }: MeasureReviewTabProps) {
  const { t } = useTranslation('measures')
  const currentUser = useMemo(() => getStoredUsername(), [])
  const reviewQuery = useMeasureReview(measure.id)
  const review = reviewQuery.data

  const setReviewers = useSetReviewers(measure.id)
  const decide = useReviewDecision(measure.id)
  const addComment = useAddReviewComment(measure.id)
  const editComment = useEditReviewComment(measure.id)
  const resolveComment = useResolveReviewComment(measure.id)

  const [newReviewer, setNewReviewer] = useState('')
  const [decisionComment, setDecisionComment] = useState('')
  const [section, setSection] = useState<string>('general')
  const [subSection, setSubSection] = useState('')
  const [body, setBody] = useState('')
  const [replyTo, setReplyTo] = useState<number | null>(null)
  const [replyBody, setReplyBody] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editBody, setEditBody] = useState('')
  const [showResolved, setShowResolved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const inReview = measure.status === MEASURE_STATUS.IN_REVIEW
  const populationAnchors = useMemo(
    () =>
      (measure.groupDefinitions ?? []).flatMap((g) =>
        (g.populations ?? []).map((p) => `${g.groupId}:${p.populationType}`),
      ),
    [measure.groupDefinitions],
  )

  const fail = (err: unknown) => setError(extractApiError(err))

  const handleAddReviewer = () => {
    const name = newReviewer.trim()
    if (!name || !review) return
    setError(null)
    setReviewers.mutate([...review.reviewers.map((r) => r.username), name], {
      onSuccess: () => setNewReviewer(''),
      onError: fail,
    })
  }

  const handleRemoveReviewer = (username: string) => {
    if (!review) return
    setError(null)
    setReviewers.mutate(review.reviewers.filter((r) => r.username !== username).map((r) => r.username), { onError: fail })
  }

  const handleDecision = (decision: 'approved' | 'changes-requested') => {
    setError(null)
    decide.mutate({ decision, comment: decisionComment.trim() || undefined }, {
      onSuccess: () => setDecisionComment(''),
      onError: fail,
    })
  }

  const handlePost = () => {
    if (!body.trim()) return
    setError(null)
    addComment.mutate(
      { body: body.trim(), section, subSection: subSection.trim() || undefined },
      { onSuccess: () => { setBody(''); setSubSection('') }, onError: fail },
    )
  }

  const handleReply = (rootId: number) => {
    if (!replyBody.trim()) return
    setError(null)
    addComment.mutate({ body: replyBody.trim(), parentId: rootId }, {
      onSuccess: () => { setReplyBody(''); setReplyTo(null) },
      onError: fail,
    })
  }

  const handleSaveEdit = (commentId: number) => {
    if (!editBody.trim()) return
    setError(null)
    editComment.mutate({ commentId, body: editBody.trim() }, {
      onSuccess: () => setEditingId(null),
      onError: fail,
    })
  }

  if (reviewQuery.isLoading) {
    return (
      <Stack direction="row" spacing={1} sx={{ p: 2, alignItems: 'center' }}>
        <CircularProgress size={18} />
        <Typography variant="body2" color="text.secondary">{t('review.loading')}</Typography>
      </Stack>
    )
  }
  if (reviewQuery.isError || !review) {
    return <Alert severity="error" sx={{ m: 2 }}>{extractApiError(reviewQuery.error)}</Alert>
  }

  const threads = showResolved ? review.comments : review.comments.filter((c) => !c.resolved)

  const renderComment = (comment: MeasureReviewComment, isReply: boolean) => (
    <Box key={comment.id} sx={{ pl: isReply ? 3 : 0, mt: isReply ? 1 : 0 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <Typography variant="subtitle2">{comment.author}</Typography>
        <Typography variant="caption" color="text.secondary">{formatTime(comment.createdAt)}</Typography>
        {comment.edited && <Typography variant="caption" color="text.secondary">({t('review.comments.edited')})</Typography>}
        {comment.author === currentUser && editingId !== comment.id && (
          <Tooltip title={t('review.comments.edit')}>
            <IconButton size="small" aria-label={t('review.comments.edit')} onClick={() => { setEditingId(comment.id); setEditBody(comment.body) }}>
              <EditIcon fontSize="inherit" />
            </IconButton>
          </Tooltip>
        )}
      </Stack>
      {editingId === comment.id ? (
        <Stack spacing={1} sx={{ mt: 0.5 }}>
          <TextField size="small" multiline minRows={2} value={editBody} onChange={(e) => setEditBody(e.target.value)} fullWidth />
          <Stack direction="row" spacing={1}>
            <Button size="small" variant="contained" onClick={() => handleSaveEdit(comment.id)} disabled={editComment.isPending}>
              {t('review.comments.save')}
            </Button>
            <Button size="small" onClick={() => setEditingId(null)}>{t('review.comments.cancel')}</Button>
          </Stack>
        </Stack>
      ) : (
        <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', mt: 0.25 }}>{comment.body}</Typography>
      )}
    </Box>
  )

  return (
    <Box sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Stack spacing={2}>
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}

        {/* ── summary ─────────────────────────────────────────────── */}
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
          <Typography variant="h6" sx={{ mr: 1 }}>{t('review.title')}</Typography>
          <Chip size="small" label={t('review.summary.reviewers', { count: review.summary.reviewers })} />
          <Chip size="small" color="success" variant="outlined" label={t('review.summary.approved', { count: review.summary.approved })} />
          <Chip size="small" color="error" variant="outlined" label={t('review.summary.changesRequested', { count: review.summary.changesRequested })} />
          <Chip size="small" variant="outlined" label={t('review.summary.pending', { count: review.summary.pending })} />
          <Chip size="small" color="warning" variant="outlined" label={t('review.summary.openThreads', { count: review.summary.openThreads })} />
        </Stack>

        {/* ── reviewers ───────────────────────────────────────────── */}
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="subtitle1" sx={{ mb: 1 }}>{t('review.reviewers.title')}</Typography>
          {review.reviewers.length === 0 && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{t('review.reviewers.none')}</Typography>
          )}
          <Stack spacing={0.5}>
            {review.reviewers.map((r: MeasureReviewer) => (
              <Stack key={r.username} direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                <Typography variant="body2" sx={{ minWidth: 120 }}>{r.username}</Typography>
                <Chip
                  size="small"
                  color={DECISION_COLOR[r.decision] ?? 'default'}
                  label={t(`review.reviewers.decision.${r.decision}`, { defaultValue: r.decision })}
                  data-testid={`reviewer-decision-${r.username}`}
                />
                {r.decisionComment && (
                  <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>{r.decisionComment}</Typography>
                )}
                {r.decidedAt && <Typography variant="caption" color="text.secondary">{formatTime(r.decidedAt)}</Typography>}
                {review.canAssign && (
                  <Tooltip title={t('review.reviewers.remove')}>
                    <IconButton size="small" color="error" aria-label={t('review.reviewers.remove')}
                      onClick={() => handleRemoveReviewer(r.username)} disabled={setReviewers.isPending}>
                      <RemoveIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
              </Stack>
            ))}
          </Stack>
          {review.canAssign && (
            <Stack direction="row" spacing={1} sx={{ mt: 1.5 }}>
              <TextField
                size="small"
                label={t('review.reviewers.add')}
                placeholder={t('review.reviewers.addPlaceholder')}
                value={newReviewer}
                onChange={(e) => setNewReviewer(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleAddReviewer()}
                sx={{ maxWidth: 320 }}
              />
              <Button size="small" variant="outlined" startIcon={<PersonAddIcon />} onClick={handleAddReviewer}
                disabled={!newReviewer.trim() || setReviewers.isPending}>
                {t('review.reviewers.assign')}
              </Button>
            </Stack>
          )}
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>{t('review.reviewers.hint')}</Typography>
        </Paper>

        {/* ── my decision ─────────────────────────────────────────── */}
        {review.reviewer && (
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
              <Typography variant="subtitle1">{t('review.decision.title')}</Typography>
              {review.myDecision && (
                <Chip size="small" color={DECISION_COLOR[review.myDecision] ?? 'default'}
                  label={t(`review.reviewers.decision.${review.myDecision}`, { defaultValue: review.myDecision })} />
              )}
            </Stack>
            {inReview ? (
              <Stack spacing={1}>
                <TextField
                  size="small"
                  multiline
                  minRows={2}
                  label={t('review.decision.commentLabel')}
                  placeholder={t('review.decision.commentPlaceholder')}
                  value={decisionComment}
                  onChange={(e) => setDecisionComment(e.target.value)}
                />
                <Stack direction="row" spacing={1}>
                  <Button size="small" variant="contained" color="success" startIcon={<CheckCircleIcon />}
                    onClick={() => handleDecision('approved')} disabled={decide.isPending}>
                    {t('review.decision.approve')}
                  </Button>
                  <Button size="small" variant="outlined" color="error" startIcon={<RateReviewIcon />}
                    onClick={() => handleDecision('changes-requested')} disabled={decide.isPending}>
                    {t('review.decision.requestChanges')}
                  </Button>
                </Stack>
                <Typography variant="caption" color="text.secondary">{t('review.decision.requestChangesHint')}</Typography>
              </Stack>
            ) : (
              <Typography variant="body2" color="text.secondary">{t('review.decision.notInReview')}</Typography>
            )}
          </Paper>
        )}

        {/* ── comments ────────────────────────────────────────────── */}
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
            <Typography variant="subtitle1">{t('review.comments.title')}</Typography>
            <FormControlLabel
              control={<Switch size="small" checked={showResolved} onChange={(e) => setShowResolved(e.target.checked)} />}
              label={<Typography variant="caption">{t('review.comments.showResolved')}</Typography>}
            />
          </Stack>

          {review.canComment && (
            <Stack spacing={1} sx={{ mb: 2 }}>
              <Stack direction="row" spacing={1}>
                <FormControl size="small" sx={{ minWidth: 160 }}>
                  <InputLabel id="review-section-label">{t('review.comments.section')}</InputLabel>
                  <Select labelId="review-section-label" label={t('review.comments.section')} value={section}
                    onChange={(e) => setSection(String(e.target.value))} inputProps={{ 'aria-label': t('review.comments.section') }}>
                    {REVIEW_SECTIONS.map((s) => (
                      <MenuItem key={s} value={s}>{t(`review.comments.sections.${s}`)}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
                {section === 'populations' && populationAnchors.length > 0 ? (
                  <FormControl size="small" sx={{ minWidth: 220 }}>
                    <InputLabel id="review-subsection-label">{t('review.comments.subSection')}</InputLabel>
                    <Select labelId="review-subsection-label" label={t('review.comments.subSection')} value={subSection}
                      onChange={(e) => setSubSection(String(e.target.value))} displayEmpty>
                      <MenuItem value="">—</MenuItem>
                      {populationAnchors.map((a) => <MenuItem key={a} value={a}>{a}</MenuItem>)}
                    </Select>
                  </FormControl>
                ) : (
                  <TextField size="small" label={t('review.comments.subSection')} placeholder={t('review.comments.subSectionPlaceholder')}
                    value={subSection} onChange={(e) => setSubSection(e.target.value)} sx={{ minWidth: 220 }} />
                )}
              </Stack>
              <TextField
                size="small"
                multiline
                minRows={2}
                label={t('review.comments.body')}
                placeholder={t('review.comments.bodyPlaceholder')}
                value={body}
                onChange={(e) => setBody(e.target.value)}
              />
              <Box>
                <Button size="small" variant="contained" onClick={handlePost} disabled={!body.trim() || addComment.isPending}>
                  {t('review.comments.post')}
                </Button>
              </Box>
            </Stack>
          )}

          {threads.length === 0 && (
            <Typography variant="body2" color="text.secondary">{t('review.comments.none')}</Typography>
          )}
          <Stack spacing={1.5}>
            {threads.map((thread) => (
              <Paper key={thread.id} variant="outlined" sx={{ p: 1.5, opacity: thread.resolved ? 0.7 : 1 }} data-testid={`review-thread-${thread.id}`}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 0.5 }}>
                  <Chip size="small" variant="outlined" label={t(`review.comments.sections.${thread.section ?? 'general'}`, { defaultValue: thread.section ?? '' })} />
                  {thread.subSection && <Chip size="small" variant="outlined" label={thread.subSection} />}
                  {thread.resolved && (
                    <Chip size="small" color="success" icon={<DoneIcon />}
                      label={t('review.comments.resolvedBy', { user: thread.resolvedBy ?? '' })} />
                  )}
                  <Box sx={{ flex: 1 }} />
                  {review.canComment && (
                    <Button
                      size="small"
                      startIcon={thread.resolved ? <UndoIcon /> : <DoneIcon />}
                      onClick={() => resolveComment.mutate({ commentId: thread.id, resolved: !thread.resolved }, { onError: fail })}
                      disabled={resolveComment.isPending}
                    >
                      {thread.resolved ? t('review.comments.unresolve') : t('review.comments.resolve')}
                    </Button>
                  )}
                </Stack>
                {renderComment(thread, false)}
                {thread.replies.length > 0 && <Divider sx={{ my: 1 }} />}
                {thread.replies.map((reply) => renderComment(reply, true))}
                {review.canComment && (
                  replyTo === thread.id ? (
                    <Stack spacing={1} sx={{ mt: 1, pl: 3 }}>
                      <TextField size="small" multiline minRows={2} autoFocus placeholder={t('review.comments.replyPlaceholder')}
                        value={replyBody} onChange={(e) => setReplyBody(e.target.value)} />
                      <Stack direction="row" spacing={1}>
                        <Button size="small" variant="contained" onClick={() => handleReply(thread.id)} disabled={!replyBody.trim() || addComment.isPending}>
                          {t('review.comments.reply')}
                        </Button>
                        <Button size="small" onClick={() => { setReplyTo(null); setReplyBody('') }}>{t('review.comments.cancel')}</Button>
                      </Stack>
                    </Stack>
                  ) : (
                    <Button size="small" startIcon={<ReplyIcon />} sx={{ mt: 0.5 }} onClick={() => setReplyTo(thread.id)}>
                      {t('review.comments.reply')}
                    </Button>
                  )
                )}
              </Paper>
            ))}
          </Stack>
        </Paper>
      </Stack>
    </Box>
  )
}

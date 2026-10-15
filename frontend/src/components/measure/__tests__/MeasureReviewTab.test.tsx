import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '../../../test/test-utils'
import MeasureReviewTab from '../MeasureReviewTab'
import type { MeasureDefinition, MeasureReview } from '../../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts && typeof opts.count === 'number' ? `${key}:${opts.count}` : key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const getReview = vi.fn()
const setReviewers = vi.fn()
const recordReviewDecision = vi.fn()
const addReviewComment = vi.fn()
const resolveReviewComment = vi.fn()
vi.mock('../../../api', () => ({
  measureApi: {
    getReview: (...a: unknown[]) => getReview(...a),
    setReviewers: (...a: unknown[]) => setReviewers(...a),
    recordReviewDecision: (...a: unknown[]) => recordReviewDecision(...a),
    addReviewComment: (...a: unknown[]) => addReviewComment(...a),
    editReviewComment: vi.fn(),
    resolveReviewComment: (...a: unknown[]) => resolveReviewComment(...a),
  },
}))
vi.mock('../../../utils/validation', () => ({ getStoredUsername: () => 'bob' }))

const measure = (status = 'in-review'): MeasureDefinition =>
  ({
    id: 5, name: 'HbA1c', version: '1.0.0', status, scoringType: 'proportion', cqlContent: '',
    groupDefinitions: [{ groupId: 'g1', populations: [{ populationType: 'initial-population', criteriaExpression: 'IP' }] }],
  }) as MeasureDefinition

const review = (over: Partial<MeasureReview> = {}): MeasureReview => ({
  measureId: 5,
  status: 'in-review',
  reviewers: [{ username: 'bob', decision: 'pending' }],
  comments: [
    {
      id: 11, author: 'alice', section: 'cql', body: 'Why 18?', edited: false, resolved: false, createdAt: '2026-10-15T01:00:00',
      replies: [{ id: 12, parentId: 11, author: 'bob', body: 'Guideline', edited: false, resolved: false, createdAt: '2026-10-15T01:05:00', replies: [] }],
    },
  ],
  summary: { reviewers: 1, approved: 0, changesRequested: 0, pending: 1, openThreads: 1, resolvedThreads: 0, comments: 2 },
  canAssign: false,
  canComment: true,
  reviewer: true,
  myDecision: 'pending',
  ...over,
})

// PAT-262 — the review tab: reviewers with decisions, the reviewer's own decision while in review,
// comment threads with replies and resolve.
describe('MeasureReviewTab', () => {
  beforeEach(() => {
    getReview.mockReset()
    setReviewers.mockReset()
    recordReviewDecision.mockReset()
    addReviewComment.mockReset()
    resolveReviewComment.mockReset()
    getReview.mockResolvedValue(review())
    recordReviewDecision.mockResolvedValue(review({ myDecision: 'approved' }))
    addReviewComment.mockResolvedValue({ id: 13, author: 'bob', body: 'x', edited: false, resolved: false, createdAt: '', replies: [] })
    resolveReviewComment.mockResolvedValue({ id: 11, resolved: true, replies: [] })
    setReviewers.mockResolvedValue(review())
  })

  it('shows reviewers with their decision, the thread with its reply, and records my decision', async () => {
    render(<MeasureReviewTab measure={measure()} />)

    expect(await screen.findByTestId('reviewer-decision-bob')).toHaveTextContent('review.reviewers.decision.pending')
    const thread = screen.getByTestId('review-thread-11')
    expect(within(thread).getByText('Why 18?')).toBeInTheDocument()
    expect(within(thread).getByText('Guideline')).toBeInTheDocument()
    expect(screen.getByText('review.summary.openThreads:1')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('review.decision.commentLabel'), { target: { value: 'fine' } })
    fireEvent.click(screen.getByRole('button', { name: 'review.decision.approve' }))
    await waitFor(() => expect(recordReviewDecision).toHaveBeenCalledWith(5, 'approved', 'fine'))
  })

  it('posts a new thread with its section, replies, and resolves a thread', async () => {
    render(<MeasureReviewTab measure={measure()} />)
    await screen.findByTestId('review-thread-11')

    fireEvent.change(screen.getByLabelText('review.comments.body'), { target: { value: 'Add exclusions' } })
    fireEvent.click(screen.getByRole('button', { name: 'review.comments.post' }))
    await waitFor(() => expect(addReviewComment).toHaveBeenCalledWith(5, { body: 'Add exclusions', section: 'general', subSection: undefined }))

    fireEvent.click(within(screen.getByTestId('review-thread-11')).getByRole('button', { name: 'review.comments.reply' }))
    fireEvent.change(screen.getByPlaceholderText('review.comments.replyPlaceholder'), { target: { value: 'Agreed' } })
    fireEvent.click(within(screen.getByTestId('review-thread-11')).getByRole('button', { name: 'review.comments.reply' }))
    await waitFor(() => expect(addReviewComment).toHaveBeenCalledWith(5, { body: 'Agreed', parentId: 11 }))

    fireEvent.click(within(screen.getByTestId('review-thread-11')).getByRole('button', { name: 'review.comments.resolve' }))
    await waitFor(() => expect(resolveReviewComment).toHaveBeenCalledWith(5, 11, true))
  })

  it('lets the owner assign and remove reviewers, and hides the decision box from non-reviewers', async () => {
    getReview.mockResolvedValue(review({ canAssign: true, reviewer: false, myDecision: null }))
    render(<MeasureReviewTab measure={measure('draft')} />)
    await screen.findByTestId('reviewer-decision-bob')

    expect(screen.queryByRole('button', { name: 'review.decision.approve' })).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('review.reviewers.add'), { target: { value: 'carol' } })
    fireEvent.click(screen.getByRole('button', { name: 'review.reviewers.assign' }))
    await waitFor(() => expect(setReviewers).toHaveBeenCalledWith(5, ['bob', 'carol']))

    fireEvent.click(screen.getByRole('button', { name: 'review.reviewers.remove' }))
    await waitFor(() => expect(setReviewers).toHaveBeenLastCalledWith(5, []))
  })
})

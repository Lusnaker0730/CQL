import React, { useState, useMemo } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  Tabs,
  Tab,
  TextField,
  MenuItem,
  Typography,
  Stack,
  Button,
  IconButton,
  Box,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { useTranslation } from 'react-i18next';

interface DateCalculatorDialogProps {
  open: boolean;
  onClose: () => void;
}

type DurationUnit = 'Days' | 'Months' | 'Years';

function calcDuration(startStr: string, endStr: string) {
  if (!startStr || !endStr) return null;
  const start = new Date(startStr);
  const end = new Date(endStr);
  if (isNaN(start.getTime()) || isNaN(end.getTime())) return null;

  const diffMs = end.getTime() - start.getTime();
  const totalDays = Math.round(diffMs / (1000 * 60 * 60 * 24));

  let years = end.getFullYear() - start.getFullYear();
  let months = end.getMonth() - start.getMonth();
  let days = end.getDate() - start.getDate();

  if (days < 0) {
    months -= 1;
    const prevMonth = new Date(end.getFullYear(), end.getMonth(), 0);
    days += prevMonth.getDate();
  }
  if (months < 0) {
    years -= 1;
    months += 12;
  }

  return { totalDays, years, months, days };
}

function computeDate(
  startStr: string,
  duration: number,
  unit: DurationUnit
): string | null {
  if (!startStr || isNaN(duration)) return null;
  const start = new Date(startStr);
  if (isNaN(start.getTime())) return null;

  const result = new Date(start);
  switch (unit) {
    case 'Days':
      result.setDate(result.getDate() + duration);
      break;
    case 'Months':
      result.setMonth(result.getMonth() + duration);
      break;
    case 'Years':
      result.setFullYear(result.getFullYear() + duration);
      break;
  }

  return result.toISOString().split('T')[0];
}

const DateCalculatorDialog: React.FC<DateCalculatorDialogProps> = ({
  open,
  onClose,
}) => {
  const { t } = useTranslation('measures');
  const [tabIndex, setTabIndex] = useState(0);

  // Duration tab state
  const [durationStart, setDurationStart] = useState('');
  const [durationEnd, setDurationEnd] = useState('');

  // Computed Date tab state
  const [computedStart, setComputedStart] = useState('');
  const [computedDuration, setComputedDuration] = useState<number>(0);
  const [computedUnit, setComputedUnit] = useState<DurationUnit>('Days');

  const durationResult = useMemo(
    () => calcDuration(durationStart, durationEnd),
    [durationStart, durationEnd]
  );

  const computedResult = useMemo(
    () => computeDate(computedStart, computedDuration, computedUnit),
    [computedStart, computedDuration, computedUnit]
  );

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        {t('dateCalculator.title')}
        <IconButton onClick={onClose} size="small" aria-label={t('dateCalculator.close')}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Tabs
          value={tabIndex}
          onChange={(_, v) => setTabIndex(v)}
          sx={{ mb: 2 }}
        >
          <Tab label={t('dateCalculator.tabs.duration')} />
          <Tab label={t('dateCalculator.tabs.computedDate')} />
        </Tabs>

        {tabIndex === 0 && (
          <Stack spacing={2}>
            <TextField
              label={t('dateCalculator.fields.startDate')}
              type="date"
              value={durationStart}
              onChange={(e) => setDurationStart(e.target.value)}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
            <TextField
              label={t('dateCalculator.fields.endDate')}
              type="date"
              value={durationEnd}
              onChange={(e) => setDurationEnd(e.target.value)}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
            {durationResult && (
              <Box
                sx={{
                  p: 2,
                  bgcolor: 'action.hover',
                  borderRadius: 1,
                }}
              >
                <Typography variant="subtitle2" gutterBottom>
                  {t('dateCalculator.result.duration')}
                </Typography>
                <Typography variant="body2">
                  {t('dateCalculator.result.daysFormat', { days: durationResult.totalDays })}
                </Typography>
                <Typography variant="body2">
                  {t('dateCalculator.result.fullFormat', { years: durationResult.years, months: durationResult.months, days: durationResult.days })}
                </Typography>
              </Box>
            )}
          </Stack>
        )}

        {tabIndex === 1 && (
          <Stack spacing={2}>
            <TextField
              label={t('dateCalculator.fields.startDate')}
              type="date"
              value={computedStart}
              onChange={(e) => setComputedStart(e.target.value)}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
            <Stack direction="row" spacing={2}>
              <TextField
                label={t('dateCalculator.fields.duration')}
                type="number"
                value={computedDuration}
                onChange={(e) =>
                  setComputedDuration(parseInt(e.target.value, 10) || 0)
                }
                fullWidth
              />
              <TextField
                label={t('dateCalculator.fields.unit')}
                select
                value={computedUnit}
                onChange={(e) =>
                  setComputedUnit(e.target.value as DurationUnit)
                }
                sx={{ minWidth: 120 }}
              >
                <MenuItem value="Days">{t('dateCalculator.units.days')}</MenuItem>
                <MenuItem value="Months">{t('dateCalculator.units.months')}</MenuItem>
                <MenuItem value="Years">{t('dateCalculator.units.years')}</MenuItem>
              </TextField>
            </Stack>
            {computedResult && (
              <Box
                sx={{
                  p: 2,
                  bgcolor: 'action.hover',
                  borderRadius: 1,
                }}
              >
                <Typography variant="subtitle2" gutterBottom>
                  {t('dateCalculator.result.resultDate')}
                </Typography>
                <Typography variant="body2">{computedResult}</Typography>
              </Box>
            )}
          </Stack>
        )}

        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 3 }}>
          <Button onClick={onClose}>{t('dateCalculator.close')}</Button>
        </Box>
      </DialogContent>
    </Dialog>
  );
};

export default DateCalculatorDialog;

import React, { useState } from 'react';
import {
  Box,
  Stack,
  Typography,
  Tabs,
  Tab,
  Chip,
  Paper,
  CircularProgress,
} from '@mui/material';
import { useTranslation } from 'react-i18next';

interface CoverageItem {
  name: string;
  type: string;
  relevance: 'TRUE' | 'FALSE' | 'NA';
  result: string;
}

interface CoverageData {
  definitions: CoverageItem[];
  functions: CoverageItem[];
}

interface TestCaseCoverageProps {
  coverage: CoverageData | null;
  isLoading: boolean;
}

const RELEVANCE_COLORS: Record<'TRUE' | 'FALSE' | 'NA', 'success' | 'error' | 'default'> = {
  TRUE: 'success',
  FALSE: 'error',
  NA: 'default',
};

const RELEVANCE_KEYS: Record<'TRUE' | 'FALSE' | 'NA', string> = {
  TRUE: 'coverage.relevance.true',
  FALSE: 'coverage.relevance.false',
  NA: 'coverage.relevance.na',
};

const CoverageItemRow: React.FC<{ item: CoverageItem }> = ({ item }) => {
  const { t } = useTranslation('measures');
  const chipLabel = t(RELEVANCE_KEYS[item.relevance]);
  const chipColor = RELEVANCE_COLORS[item.relevance];
  return (
    <Paper
      variant="outlined"
      sx={{
        p: 1.5,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 2,
      }}
    >
      <Typography sx={{ fontWeight: 500, flex: 1, minWidth: 0 }}>
        {item.name}
      </Typography>
      <Chip label={chipLabel} color={chipColor} size="small" />
      <Typography
        variant="caption"
        color="text.secondary"
        sx={{ fontFamily: 'monospace', minWidth: 80, textAlign: 'right' }}
      >
        {item.result}
      </Typography>
    </Paper>
  );
};

const TestCaseCoverage: React.FC<TestCaseCoverageProps> = ({
  coverage,
  isLoading,
}) => {
  const { t } = useTranslation('measures');
  const [tabIndex, setTabIndex] = useState(0);

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!coverage) {
    return (
      <Box sx={{ py: 3, textAlign: 'center' }}>
        <Typography color="text.secondary">{t('coverage.noData')}</Typography>
      </Box>
    );
  }

  const items = tabIndex === 0 ? coverage.definitions : coverage.functions;
  const label = tabIndex === 0 ? t('coverage.labels.definitions') : t('coverage.labels.functions');
  const coveredCount = items.filter((i) => i.relevance === 'TRUE').length;
  const totalCount = items.length;

  return (
    <Box>
      <Tabs
        value={tabIndex}
        onChange={(_, v) => setTabIndex(v)}
        sx={{ mb: 1 }}
      >
        <Tab label={t('coverage.tabs.definitions')} />
        <Tab label={t('coverage.tabs.functions')} />
      </Tabs>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
        {t('coverage.covered', { covered: coveredCount, total: totalCount, label })}
      </Typography>

      <Stack spacing={1}>
        {items.map((item, index) => (
          <CoverageItemRow key={`${item.name}-${index}`} item={item} />
        ))}
      </Stack>
    </Box>
  );
};

export default TestCaseCoverage;

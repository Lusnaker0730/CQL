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

const relevanceChipProps: Record<
  'TRUE' | 'FALSE' | 'NA',
  { label: string; color: 'success' | 'error' | 'default' }
> = {
  TRUE: { label: 'TRUE', color: 'success' },
  FALSE: { label: 'FALSE', color: 'error' },
  NA: { label: 'NA', color: 'default' },
};

const CoverageItemRow: React.FC<{ item: CoverageItem }> = ({ item }) => {
  const chipProps = relevanceChipProps[item.relevance];
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
      <Chip label={chipProps.label} color={chipProps.color} size="small" />
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
        <Typography color="text.secondary">No coverage data</Typography>
      </Box>
    );
  }

  const items = tabIndex === 0 ? coverage.definitions : coverage.functions;
  const label = tabIndex === 0 ? 'definitions' : 'functions';
  const coveredCount = items.filter((i) => i.relevance === 'TRUE').length;
  const totalCount = items.length;

  return (
    <Box>
      <Tabs
        value={tabIndex}
        onChange={(_, v) => setTabIndex(v)}
        sx={{ mb: 1 }}
      >
        <Tab label="Definitions" />
        <Tab label="Functions" />
      </Tabs>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
        {coveredCount}/{totalCount} {label} covered
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

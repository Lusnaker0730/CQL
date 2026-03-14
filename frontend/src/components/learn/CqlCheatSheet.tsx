import {
  Box,
  Typography,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material'
import { ExpandMore as ExpandMoreIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'

const BASIC_STRUCTURE_KEYS = [
  'library', 'using', 'include', 'codesystem', 'valueset', 'code',
  'parameter', 'context', 'define', 'function', 'commentSingle', 'commentMulti',
] as const

const DATA_ACCESS_KEYS = [
  'retrieve', 'retrieveCode', 'alias', 'where', 'exists', 'notExists',
  'codeIn', 'sort', 'first', 'with', 'without', 'return', 'distinct',
] as const

const DATA_TYPE_KEYS = [
  'boolean', 'integer', 'decimal', 'string', 'dateTime',
  'time', 'quantity', 'interval', 'list', 'tuple',
] as const

const OPERATOR_KEYS = [
  'comparison', 'arithmetic', 'stringOps', 'dateTimeOps', 'listOps', 'logical',
] as const

const RETRIEVE_KEYS = ['basicRetrieve', 'codeFilter', 'dateFilter'] as const

const FUNCTION_KEYS = [
  'ageInYears', 'count', 'exists', 'first', 'last',
  'sum', 'avg', 'toString', 'toDateTime', 'flatten',
] as const

export default function CqlCheatSheet() {
  const { t } = useTranslation('landing')

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} gutterBottom color="secondary.main">
        {t('learn.cheatSheet.title')}
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        {t('learn.cheatSheet.subtitle')}
      </Typography>

      {/* Basic Structure */}
      <Accordion defaultExpanded>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="h6" fontWeight={700} color="primary.main">
            {t('learn.cheatSheet.basicStructure.title')}
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700, width: '25%' }}>{t('learn.cheatSheet.basicStructure.element')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('learn.cheatSheet.basicStructure.syntax')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {BASIC_STRUCTURE_KEYS.map((key) => (
                  <TableRow key={key}>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>{t(`learn.cheatSheet.basicStructure.${key}`)}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'primary.main', whiteSpace: 'pre-wrap' }}>
                        {t(`learn.cheatSheet.basicStructure.${key}Ex`)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </AccordionDetails>
      </Accordion>

      {/* Data Access & Filtering */}
      <Accordion defaultExpanded>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="h6" fontWeight={700} color="primary.main">
            {t('learn.cheatSheet.dataAccess.title')}
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700, width: '25%' }}>{t('learn.cheatSheet.dataAccess.pattern')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('learn.cheatSheet.dataAccess.syntax')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {DATA_ACCESS_KEYS.map((key) => (
                  <TableRow key={key}>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>{t(`learn.cheatSheet.dataAccess.${key}`)}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'primary.main', whiteSpace: 'pre-wrap' }}>
                        {t(`learn.cheatSheet.dataAccess.${key}Ex`)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </AccordionDetails>
      </Accordion>

      {/* Data Types */}
      <Accordion>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="h6" fontWeight={700} color="primary.main">
            {t('learn.cheatSheet.dataTypes.title')}
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>{t('learn.cheatSheet.dataTypes.type')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('learn.cheatSheet.dataTypes.example')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {DATA_TYPE_KEYS.map((key) => (
                  <TableRow key={key}>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>{t(`learn.cheatSheet.dataTypes.${key}`)}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'primary.main' }}>
                        {t(`learn.cheatSheet.dataTypes.${key}Ex`)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </AccordionDetails>
      </Accordion>

      {/* Operators */}
      <Accordion>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="h6" fontWeight={700} color="primary.main">
            {t('learn.cheatSheet.operators.title')}
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>{t('learn.cheatSheet.operators.category')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('learn.cheatSheet.operators.operatorCol')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {OPERATOR_KEYS.map((key) => (
                  <TableRow key={key}>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>{t(`learn.cheatSheet.operators.${key}`)}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                        {t(`learn.cheatSheet.operators.${key}Ops` as `learn.cheatSheet.operators.${string}`)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </AccordionDetails>
      </Accordion>

      {/* FHIR Retrieve Patterns */}
      <Accordion>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="h6" fontWeight={700} color="primary.main">
            {t('learn.cheatSheet.retrieve.title')}
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>{t('learn.cheatSheet.retrieve.pattern')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>CQL</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {RETRIEVE_KEYS.map((key) => (
                  <TableRow key={key}>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>{t(`learn.cheatSheet.retrieve.${key}`)}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'primary.main' }}>
                        {t(`learn.cheatSheet.retrieve.${key}Ex`)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </AccordionDetails>
      </Accordion>

      {/* Common Functions */}
      <Accordion>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="h6" fontWeight={700} color="primary.main">
            {t('learn.cheatSheet.functions.title')}
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>{t('learn.cheatSheet.functions.function')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('learn.cheatSheet.functions.description')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {FUNCTION_KEYS.map((key) => (
                  <TableRow key={key}>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
                        {t(`learn.cheatSheet.functions.${key}`)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {t(`learn.cheatSheet.functions.${key}Desc`)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </AccordionDetails>
      </Accordion>
    </Box>
  )
}

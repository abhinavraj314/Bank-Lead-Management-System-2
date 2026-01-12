import { parse } from 'csv-parse/sync';
import { normalizeHeaders, normalizeRowValues, validateIdentifiers, NormalizedLeadInput } from './leadNormalization';

export interface ParsedRow {
  row: number;
  data: NormalizedLeadInput;
  errors: string[];
}

export interface ParseResult {
  success: boolean;
  totalRows: number;
  validRows: ParsedRow[];
  invalidRows: ParsedRow[];
}

/**
 * Parse CSV file buffer into normalized lead data
 * @param fileBuffer - CSV file buffer
 * @returns ParseResult with valid and invalid rows
 */
export const parseCSV = (fileBuffer: Buffer): ParseResult => {
  try {
    const content = fileBuffer.toString('utf-8');
    
    // Parse CSV with headers
    const records = parse(content, {
      columns: true,
      skip_empty_lines: true,
      trim: true,
      relaxColumnCount: true
    });

    if (!records || records.length === 0) {
      return {
        success: false,
        totalRows: 0,
        validRows: [],
        invalidRows: [{
          row: 1,
          data: {},
          errors: ['CSV file is empty or has no data rows']
        }]
      };
    }

    const validRows: ParsedRow[] = [];
    const invalidRows: ParsedRow[] = [];

    // Get headers from first row
    const headers = Object.keys(records[0] as Record<string, any>);

    const headerMapping = normalizeHeaders(headers);

    // Process each row
    records.forEach((record: any, index: number) => {
      const rowNumber = index + 2; // +2 because index is 0-based and we skip header row

      try {
        // Normalize row values
        const normalized = normalizeRowValues(record, headerMapping);

        // Validate that at least one identifier exists
        const validation = validateIdentifiers(normalized);

        if (!validation.valid) {
          invalidRows.push({
            row: rowNumber,
            data: normalized,
            errors: [validation.reason || 'Missing required identifier']
          });
          return;
        }

        validRows.push({
          row: rowNumber,
          data: normalized,
          errors: []
        });
      } catch (error: any) {
        invalidRows.push({
          row: rowNumber,
          data: {},
          errors: [error.message || 'Failed to parse row']
        });
      }
    });

    return {
      success: validRows.length > 0,
      totalRows: records.length,
      validRows,
      invalidRows
    };
  } catch (error: any) {
    return {
      success: false,
      totalRows: 0,
      validRows: [],
      invalidRows: [{
        row: 1,
        data: {},
        errors: [error.message || 'Failed to parse CSV file']
      }]
    };
  }
};

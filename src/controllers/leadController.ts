import { Request, Response } from 'express';
import { Lead } from '../models/Lead';
import { Product } from '../models/Product';
import { Source } from '../models/Source';
import multer from 'multer';
import ExcelJS from 'exceljs';
import {
  normalizeHeaders,
  normalizeRowValues,
  validateIdentifiers
} from '../utils/leadNormalization';
import { upsertLead } from '../services/leadService';
import { leadUploadSchema } from '../utils/validation';
import { parseCSV } from '../utils/csvParser';
import { sendSuccess, sendError, sendSuccessWithPagination, calculatePagination } from '../utils/responseHandler';
import { v4 as uuidv4 } from 'uuid';

const storage = multer.memoryStorage();
const upload = multer({
  storage,
  limits: {
    fileSize: 10 * 1024 * 1024
  },
  fileFilter: (_req, file, cb) => {
    const allowedTypes = [
      'text/csv',
      'application/vnd.ms-excel',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    ];
    
    if (allowedTypes.includes(file.mimetype) || 
        file.originalname.match(/\.(csv|xls|xlsx)$/i)) {
      cb(null, true);
    } else {
      cb(new Error('Only CSV and Excel files are allowed'));
    }
  }
});

export const uploadMiddleware = upload.single('file');

/**
 * Upload leads from CSV/Excel file
 * POST /api/leads/upload
 */
export const uploadLeads = async (req: Request, res: Response): Promise<void> => {
  try {
    const file = req.file;
    
    if (!file) {
      sendError(res, 'File is required', 400);
      return;
    }
    
    const { error, value } = leadUploadSchema.validate(req.body);
    if (error) {
      sendError(res, error.details[0].message, 400);
      return;
    }
    
    const { p_id, source_id } = value;
    
    const [product, source] = await Promise.all([
      Product.findOne({ p_id }),
      Source.findOne({ source_id })
    ]);
    
    if (!product) {
      sendError(res, `Product '${p_id}' not found`, 400);
      return;
    }
    
    if (!source) {
      sendError(res, `Source '${source_id}' not found`, 400);
      return;
    }
    
    let rows: any[] = [];
    const filename = file.originalname.toLowerCase();
    
    if (filename.endsWith('.csv')) {
      // Parse CSV
      const parseResult = parseCSV(file.buffer);
      
      if (!parseResult.success) {
        sendError(res, 'Failed to parse CSV file', 400);
        return;
      }
      
      // Process valid rows
      for (const parsedRow of parseResult.validRows) {
        rows.push(parsedRow.data);
      }
      
      // Track failed rows
      const failedRows = parseResult.invalidRows.map(r => ({
        row: r.row,
        errors: r.errors,
        data: r.data
      }));
      
      if (rows.length === 0 && failedRows.length > 0) {
        sendError(res, 'No valid rows found in CSV', 400, { failedRows });
        return;
      }
    } else if (filename.endsWith('.xlsx') || filename.endsWith('.xls')) {
      try {
        const workbook = new ExcelJS.Workbook();
        await workbook.xlsx.load(file.buffer as any);
        
        const worksheet = workbook.worksheets[0];
        if (!worksheet) {
          sendError(res, 'Excel file has no sheets', 400);
          return;
        }
        
        const headers: string[] = [];
        let isFirstRow = true;
        
        worksheet.eachRow((row: any) => {
          if (isFirstRow) {
            row.eachCell((cell: any) => {
              headers.push(String(cell.value || ''));
            });
            isFirstRow = false;
          } else {
            const rowData: any = {};
            let colIndex = 0;
            
            row.eachCell({ includeEmpty: true }, (cell: any) => {
              const header = headers[colIndex];
              if (header) {
                rowData[header] = String(cell.value || '');
              }
              colIndex++;
            });
            
            if (Object.keys(rowData).length > 0) {
              rows.push(rowData);
            }
          }
        });
      } catch (excelError: any) {
        sendError(res, 'Failed to parse Excel file: ' + excelError.message, 400);
        return;
      }
    } else {
      sendError(res, 'Unsupported file format. Use CSV or Excel (.xlsx, .xls)', 400);
      return;
    }
    
    if (!rows || rows.length === 0) {
      sendError(res, 'File contains no data rows', 400);
      return;
    }
    
    const headers = Object.keys(rows[0]);
    const headerMapping = normalizeHeaders(headers);
    
    let insertedCount = 0;
    let mergedCount = 0;
    let failedCount = 0;
    const failedRows: Array<{
      row: number;
      reason: string;
      raw: any;
    }> = [];
    
    for (let i = 0; i < rows.length; i++) {
      const rawRow = rows[i];
      
      try {
        const normalized = normalizeRowValues(rawRow, headerMapping);
        
        const validation = validateIdentifiers(normalized);
        if (!validation.valid) {
          failedCount++;
          failedRows.push({
            row: i + 1,
            reason: validation.reason!,
            raw: rawRow
          });
          continue;
        }
        
        const result = await upsertLead(normalized, {
          p_id,
          source_id,
          rawRow
        });
        
        if (result.action === 'inserted') {
          insertedCount++;
        } else {
          mergedCount++;
        }
      } catch (error: any) {
        failedCount++;
        failedRows.push({
          row: i + 1,
          reason: error.message || 'Processing error',
          raw: rawRow
        });
      }
    }
    
    sendSuccess(res, {
      totalRows: rows.length,
      insertedCount,
      mergedCount,
      failedCount,
      failedRows: failedRows.slice(0, 100)
    }, 'Upload completed');
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

/**
 * Get all leads with pagination and filters
 * GET /api/leads
 */
export const getLeads = async (req: Request, res: Response): Promise<void> => {
  try {
    const {
      p_id,
      source_id,
      from,
      to,
      q,
      page = '1',
      limit = '20',
      sort = 'created_at',
      order = 'desc'
    } = req.query;
    
    const filter: any = {};
    
    if (p_id) {
      filter.p_id = String(p_id).toUpperCase();
    }
    
    if (source_id) {
      filter.source_id = String(source_id).toUpperCase();
    }
    
    if (from || to) {
      filter.created_at = {};
      if (from) {
        filter.created_at.$gte = new Date(String(from));
      }
      if (to) {
        filter.created_at.$lte = new Date(String(to));
      }
    }
    
    if (q) {
      const searchTerm = String(q).trim();
      filter.$or = [
        { name: { $regex: searchTerm, $options: 'i' } },
        { email: { $regex: searchTerm, $options: 'i' } },
        { phone_number: { $regex: searchTerm, $options: 'i' } }
      ];
    }
    
    const pageNum = Math.max(1, parseInt(String(page), 10));
    const limitNum = Math.min(100, Math.max(1, parseInt(String(limit), 10)));
    const skip = (pageNum - 1) * limitNum;
    
    const sortField = String(sort);
    const sortOrder = order === 'asc' ? 1 : -1;
    const sortObj: any = { [sortField]: sortOrder };
    
    const [leads, totalCount] = await Promise.all([
      Lead.find(filter)
        .sort(sortObj)
        .skip(skip)
        .limit(limitNum)
        .select('-merged_from'),
      Lead.countDocuments(filter)
    ]);
    
    sendSuccessWithPagination(
      res,
      leads,
      calculatePagination(pageNum, limitNum, totalCount)
    );
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

/**
 * Get single lead by lead_id
 * GET /api/leads/:id
 */
export const getLeadById = async (req: Request, res: Response): Promise<void> => {
  try {
    const lead = await Lead.findOne({ lead_id: req.params.id });
    
    if (!lead) {
      sendError(res, `Lead with lead_id '${req.params.id}' not found`, 404);
      return;
    }
    
    sendSuccess(res, lead);
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

/**
 * Create new lead
 * POST /api/leads
 */
export const createLead = async (req: Request, res: Response): Promise<void> => {
  try {
    const { name, phone_number, email, aadhar_number, p_id, source_id } = req.body;
    
    // Validate at least one identifier
    if (!phone_number && !email && !aadhar_number) {
      sendError(res, 'At least one identifier (phone_number, email, or aadhar_number) is required', 400);
      return;
    }
    
    // Validate product and source if provided
    if (p_id) {
      const product = await Product.findOne({ p_id: p_id.toUpperCase() });
      if (!product) {
        sendError(res, `Product '${p_id}' not found`, 400);
        return;
      }
    }
    
    if (source_id) {
      const source = await Source.findOne({ source_id: source_id.toUpperCase() });
      if (!source) {
        sendError(res, `Source '${source_id}' not found`, 400);
        return;
      }
    }
    
    // Normalize data first
    const normalizedEmail = email ? email.toLowerCase().trim() : undefined;
    const normalizedPhone = phone_number ? phone_number.replace(/\D/g, '').slice(-10) : undefined;
    const normalizedAadhar = aadhar_number ? aadhar_number.replace(/\D/g, '') : undefined;
    
    // Check for duplicates
    const duplicateConditions: any[] = [];
    if (normalizedEmail) {
      duplicateConditions.push({ email: normalizedEmail });
    }
    if (normalizedPhone) {
      duplicateConditions.push({ phone_number: normalizedPhone });
    }
    if (normalizedAadhar) {
      duplicateConditions.push({ aadhar_number: normalizedAadhar });
    }
    
    if (duplicateConditions.length > 0) {
      const existing = await Lead.findOne({ $or: duplicateConditions });
      
      if (existing) {
        sendError(res, 'Lead with matching identifier already exists', 409);
        return;
      }
    }
    
    const lead = new Lead({
      lead_id: uuidv4(),
      name: name?.trim(),
      phone_number: normalizedPhone,
      email: normalizedEmail,
      aadhar_number: normalizedAadhar,
      source_id: source_id?.toUpperCase(),
      p_id: p_id?.toUpperCase(),
      created_at: new Date(),
      merged_from: [],
      sources_seen: source_id ? [source_id.toUpperCase()] : [],
      products_seen: p_id ? [p_id.toUpperCase()] : [],
      lead_score: null,
      score_reason: null
    });
    
    await lead.save();
    
    sendSuccess(res, lead, 'Lead created successfully', 201);
  } catch (error: any) {
    if (error.code === 11000) {
      sendError(res, 'Lead with this identifier already exists', 409);
    } else {
      sendError(res, error.message, 500);
    }
  }
};

/**
 * Update lead
 * PUT /api/leads/:id
 */
export const updateLead = async (req: Request, res: Response): Promise<void> => {
  try {
    const lead = await Lead.findOne({ lead_id: req.params.id });
    
    if (!lead) {
      sendError(res, `Lead with lead_id '${req.params.id}' not found`, 404);
      return;
    }
    
    // Update fields
    if (req.body.name !== undefined) {
      lead.name = req.body.name?.trim();
    }
    
    if (req.body.phone_number !== undefined) {
      lead.phone_number = req.body.phone_number ? req.body.phone_number.replace(/\D/g, '').slice(-10) : undefined;
    }
    
    if (req.body.email !== undefined) {
      lead.email = req.body.email ? req.body.email.toLowerCase().trim() : undefined;
    }
    
    if (req.body.aadhar_number !== undefined) {
      lead.aadhar_number = req.body.aadhar_number ? req.body.aadhar_number.replace(/\D/g, '') : undefined;
    }
    
    if (req.body.p_id !== undefined && req.body.p_id) {
      const pId = String(req.body.p_id).toUpperCase();
      const product = await Product.findOne({ p_id: pId });
      if (!product) {
        sendError(res, `Product '${req.body.p_id}' not found`, 400);
        return;
      }
      lead.p_id = pId;
      if (lead.products_seen && !lead.products_seen.includes(pId)) {
        lead.products_seen.push(pId);
      }
    }
    
    if (req.body.source_id !== undefined && req.body.source_id) {
      const sourceId = String(req.body.source_id).toUpperCase();
      const source = await Source.findOne({ source_id: sourceId });
      if (!source) {
        sendError(res, `Source '${req.body.source_id}' not found`, 400);
        return;
      }
      lead.source_id = sourceId;
      if (lead.sources_seen && !lead.sources_seen.includes(sourceId)) {
        lead.sources_seen.push(sourceId);
      }
    }
    
    await lead.save();
    
    sendSuccess(res, lead, 'Lead updated successfully');
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

/**
 * Delete lead
 * DELETE /api/leads/:id
 */
export const deleteLead = async (req: Request, res: Response): Promise<void> => {
  try {
    const lead = await Lead.findOneAndDelete({ lead_id: req.params.id });
    
    if (!lead) {
      sendError(res, `Lead with lead_id '${req.params.id}' not found`, 404);
      return;
    }
    
    sendSuccess(res, { message: 'Lead deleted successfully' });
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

/**
 * Get merge history for a lead
 * GET /api/leads/:id/history
 */
export const getLeadHistory = async (req: Request, res: Response): Promise<void> => {
  try {
    const lead = await Lead.findOne({ lead_id: req.params.id });
    
    if (!lead) {
      sendError(res, `Lead with lead_id '${req.params.id}' not found`, 404);
      return;
    }
    
    sendSuccess(res, {
      lead_id: lead.lead_id,
      merged_from: lead.merged_from,
      sources_seen: lead.sources_seen,
      products_seen: lead.products_seen,
      created_at: lead.created_at
    });
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

/**
 * Score lead (stub)
 * POST /api/leads/:id/score
 */
export const scoreLeadStub = async (req: Request, res: Response): Promise<void> => {
  try {
    const lead = await Lead.findOne({ lead_id: req.params.id });
    
    if (!lead) {
      sendError(res, `Lead with lead_id '${req.params.id}' not found`, 404);
      return;
    }
    
    // Import scoring service
    const { scoreLead } = await import('../services/leadScoringService');
    const result = await scoreLead(lead);
    
    sendSuccess(res, {
      lead_id: lead.lead_id,
      lead_score: result.score,
      score_reason: result.reason,
      breakdown: result.breakdown
    }, 'Lead scored successfully');
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

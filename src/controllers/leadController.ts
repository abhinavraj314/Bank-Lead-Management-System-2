import { Request, Response } from 'express';
import { Lead } from '../models/Lead';
import { Product } from '../models/Product';
import { Source } from '../models/Source';
import multer from 'multer';
import { parse as csvParse } from 'csv-parse';
import ExcelJS from 'exceljs';  // ✅ FIXED
import {
  normalizeHeaders,
  normalizeRowValues,
  validateIdentifiers
} from '../utils/leadNormalization';
import { upsertLead } from '../services/leadService';
import { leadUploadSchema } from '../utils/validation';

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

export const uploadLeads = async (req: Request, res: Response): Promise<void> => {
  try {
    const file = req.file;
    
    if (!file) {
      res.status(400).json({ error: 'File is required' });
      return;
    }
    
    const { error, value } = leadUploadSchema.validate(req.body);
    if (error) {
      res.status(400).json({ error: error.details[0].message });
      return;
    }
    
    const { p_id, source_id } = value;
    
    const [product, source] = await Promise.all([
      Product.findOne({ p_id }),
      Source.findOne({ source_id })
    ]);
    
    if (!product) {
      res.status(400).json({ error: `Product '${p_id}' not found` });
      return;
    }
    
    if (!source) {
      res.status(400).json({ error: `Source '${source_id}' not found` });
      return;
    }
    
    let rows: any[] = [];
    const filename = file.originalname.toLowerCase();
    
    if (filename.endsWith('.csv')) {
      const content = file.buffer.toString('utf-8');
      rows = await new Promise<any[]>((resolve, reject) => {
        csvParse(
          content,
          {
            columns: true,
            skip_empty_lines: true,
            trim: true,
            relaxColumnCount: true
          },
          (err, records) => {
            if (err) return reject(err);
            resolve(records);
          }
        );
      });
    } else if (filename.endsWith('.xlsx') || filename.endsWith('.xls')) {
      // ✅ FIXED: ExcelJS parsing
      try {
        const workbook = new ExcelJS.Workbook();
        await workbook.xlsx.load(file.buffer as any);

        
        const worksheet = workbook.worksheets[0];
        if (!worksheet) {
          res.status(400).json({ error: 'Excel file has no sheets' });
          return;
        }
        
        rows = [];
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
        res.status(400).json({ 
          error: 'Failed to parse Excel file: ' + excelError.message 
        });
        return;
      }
    } else {
      res.status(400).json({
        error: 'Unsupported file format. Use CSV or Excel (.xlsx, .xls)'
      });
      return;
    }
    
    if (!rows || rows.length === 0) {
      res.status(400).json({ error: 'File contains no data rows' });
      return;
    }
    
    const headers = Object.keys(rows[0]);
    const headerMapping = normalizeHeaders(headers);
    
    let insertedCount = 0;
    let mergedCount = 0;
    let failedCount = 0;
    const failedRows: Array<{
      index: number;
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
            index: i + 1,
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
          index: i + 1,
          reason: error.message || 'Processing error',
          raw: rawRow
        });
      }
    }
    
    res.json({
      success: true,
      totalRows: rows.length,
      insertedCount,
      mergedCount,
      failedCount,
      failedRows: failedRows.slice(0, 100)
    });
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

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
    
    res.json({
      leads,
      pagination: {
        page: pageNum,
        limit: limitNum,
        total: totalCount,
        totalPages: Math.ceil(totalCount / limitNum)
      }
    });
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const getLeadById = async (req: Request, res: Response): Promise<void> => {
  try {
    const lead = await Lead.findOne({ lead_id: req.params.id });
    
    if (!lead) {
      res.status(404).json({
        error: `Lead with lead_id '${req.params.id}' not found`
      });
      return;
    }
    
    res.json(lead);
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const scoreLeadStub = async (req: Request, res: Response): Promise<void> => {
  try {
    const lead = await Lead.findOne({ lead_id: req.params.id });
    
    if (!lead) {
      res.status(404).json({
        error: `Lead with lead_id '${req.params.id}' not found`
      });
      return;
    }
    
    lead.lead_score = 0;
    lead.score_reason = 'AI scoring not implemented yet - placeholder';
    await lead.save();
    
    res.json({
      message: 'AI scoring stub executed (not implemented)',
      lead_id: lead.lead_id,
      lead_score: lead.lead_score,
      score_reason: lead.score_reason,
      note: 'This is a placeholder. Actual AI scoring will be implemented in future phases.'
    });
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};




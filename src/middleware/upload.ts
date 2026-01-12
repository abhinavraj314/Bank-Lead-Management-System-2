import multer from 'multer';
import { Request } from 'express';

/**
 * Configure multer for file uploads
 * Store files in memory (not on disk)
 */
const storage = multer.memoryStorage();

/**
 * File filter to accept only CSV files
 */
const fileFilter = (req: Request, file: Express.Multer.File, cb: multer.FileFilterCallback): void => {
  // Accept CSV files
  if (
    file.mimetype === 'text/csv' ||
    file.mimetype === 'application/vnd.ms-excel' ||
    file.originalname.match(/\.csv$/i)
  ) {
    cb(null, true);
  } else {
    cb(new Error('Only CSV files are allowed'));
  }
};

/**
 * Multer configuration for CSV uploads
 * Max file size: 10MB
 */
export const uploadCSV = multer({
  storage,
  limits: {
    fileSize: 10 * 1024 * 1024 // 10MB
  },
  fileFilter
});

/**
 * Middleware to validate file exists
 */
export const validateFile = (req: Request, res: any, next: any): void => {
  if (!req.file) {
    res.status(400).json({
      success: false,
      error: {
        message: 'CSV file is required',
        details: {
          acceptedFormats: ['csv'],
          maxSize: '10MB'
        }
      }
    });
    return;
  }
  next();
};

import { Request } from 'express';

declare global {
  namespace Express {
    interface Request {
      user?: any; // Add user property if needed in future
      file?: Express.Multer.File;
      files?: Express.Multer.File[];
    }
  }
}

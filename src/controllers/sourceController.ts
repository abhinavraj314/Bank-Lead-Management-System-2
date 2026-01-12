import { Request, Response } from 'express';
import { Source } from '../models/Source';
import { Product } from '../models/Product';
import { Lead } from '../models/Lead';
import { sourceSchema, sourceUpdateSchema } from '../utils/validation';
import { sendSuccess, sendError, sendSuccessWithPagination, calculatePagination } from '../utils/responseHandler';

export const createSource = async (req: Request, res: Response): Promise<void> => {
  try {
    const { error, value } = sourceSchema.validate(req.body);
    if (error) {
      sendError(res, error.details[0].message, 400);
      return;
    }
    
    // Validate that product exists
    const product = await Product.findOne({ p_id: value.p_id });
    if (!product) {
      sendError(res, `Product with p_id '${value.p_id}' does not exist`, 400);
      return;
    }
    
    // Check for duplicate source_id
    const existingSource = await Source.findOne({ source_id: value.source_id });
    if (existingSource) {
      sendError(res, `Source with source_id '${value.source_id}' already exists`, 409);
      return;
    }
    
    const source = await Source.create(value);
    sendSuccess(res, source, 'Source created successfully', 201);
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

export const getSources = async (req: Request, res: Response): Promise<void> => {
  try {
    const page = Math.max(1, parseInt(req.query.page as string) || 1);
    const limit = Math.min(100, Math.max(1, parseInt(req.query.limit as string) || 10));
    const skip = (page - 1) * limit;

    const filter: any = {};
    
    // Filter by productId if provided
    if (req.query.productId || req.query.p_id) {
      filter.p_id = String(req.query.productId || req.query.p_id).toUpperCase();
    }
    
    const [sources, total] = await Promise.all([
      Source.find(filter).sort({ createdAt: -1 }).skip(skip).limit(limit),
      Source.countDocuments(filter)
    ]);
    
    sendSuccessWithPagination(res, sources, calculatePagination(page, limit, total));
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

export const getSourceById = async (req: Request, res: Response): Promise<void> => {
  try {
    const source = await Source.findOne({ source_id: req.params.id.toUpperCase() });
    
    if (!source) {
      sendError(res, `Source with source_id '${req.params.id}' not found`, 404);
      return;
    }
    
    sendSuccess(res, source);
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

export const updateSource = async (req: Request, res: Response): Promise<void> => {
  try {
    const { error, value } = sourceUpdateSchema.validate(req.body);
    if (error) {
      sendError(res, error.details[0].message, 400);
      return;
    }
    
    // If updating p_id, validate the new product exists
    if (value.p_id) {
      const product = await Product.findOne({ p_id: value.p_id });
      if (!product) {
        sendError(res, `Product with p_id '${value.p_id}' does not exist`, 400);
        return;
      }
    }
    
    const source = await Source.findOneAndUpdate(
      { source_id: req.params.id.toUpperCase() },
      value,
      { new: true, runValidators: true }
    );
    
    if (!source) {
      sendError(res, `Source with source_id '${req.params.id}' not found`, 404);
      return;
    }
    
    sendSuccess(res, source, 'Source updated successfully');
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

export const deleteSource = async (req: Request, res: Response): Promise<void> => {
  try {
    const source_id = req.params.id.toUpperCase();
    
    // Check if leads exist for this source
    const leadsCount = await Lead.countDocuments({ source_id });
    if (leadsCount > 0) {
      sendError(res, `Cannot delete source: ${leadsCount} lead(s) are associated with this source`, 409);
      return;
    }
    
    const source = await Source.findOneAndDelete({ source_id });
    
    if (!source) {
      sendError(res, `Source with source_id '${req.params.id}' not found`, 404);
      return;
    }
    
    sendSuccess(res, { message: 'Source deleted successfully' });
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};





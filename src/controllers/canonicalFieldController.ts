import { Request, Response } from 'express';
import { CanonicalField } from '../models/CanonicalField';
import { sendSuccess, sendError, sendSuccessWithPagination, calculatePagination } from '../utils/responseHandler';

/**
 * Get all canonical fields with pagination
 * GET /api/canonical-fields
 */
export const getCanonicalFields = async (req: Request, res: Response): Promise<void> => {
  try {
    const page = Math.max(1, parseInt(req.query.page as string) || 1);
    const limit = Math.min(100, Math.max(1, parseInt(req.query.limit as string) || 10));
    const skip = (page - 1) * limit;

    const filter: any = {};
    
    // Filter by is_active if provided
    if (req.query.is_active !== undefined) {
      filter.is_active = req.query.is_active === 'true';
    }

    const [fields, total] = await Promise.all([
      CanonicalField.find(filter)
        .sort({ field_name: 1 })
        .skip(skip)
        .limit(limit),
      CanonicalField.countDocuments(filter)
    ]);

    sendSuccessWithPagination(
      res,
      fields,
      calculatePagination(page, limit, total)
    );
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

/**
 * Get single canonical field by field_name
 * GET /api/canonical-fields/:id
 */
export const getCanonicalFieldById = async (req: Request, res: Response): Promise<void> => {
  try {
    const field = await CanonicalField.findOne({ 
      field_name: req.params.id.toLowerCase() 
    });

    if (!field) {
      sendError(res, `Canonical field with name '${req.params.id}' not found`, 404);
      return;
    }

    sendSuccess(res, field);
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

/**
 * Create new canonical field
 * POST /api/canonical-fields
 */
export const createCanonicalField = async (req: Request, res: Response): Promise<void> => {
  try {
    // Check for duplicate field_name
    const existing = await CanonicalField.findOne({ 
      field_name: req.body.field_name?.toLowerCase() 
    });

    if (existing) {
      sendError(res, `Canonical field with name '${req.body.field_name}' already exists`, 409);
      return;
    }

    const field = new CanonicalField({
      ...req.body,
      field_name: req.body.field_name?.toLowerCase()
    });

    await field.save();
    sendSuccess(res, field, 'Canonical field created successfully', 201);
  } catch (error: any) {
    if (error.code === 11000) {
      sendError(res, 'Canonical field with this name already exists', 409);
    } else {
      sendError(res, error.message, 400);
    }
  }
};

/**
 * Update canonical field
 * PUT /api/canonical-fields/:id
 */
export const updateCanonicalField = async (req: Request, res: Response): Promise<void> => {
  try {
    const field = await CanonicalField.findOneAndUpdate(
      { field_name: req.params.id.toLowerCase() },
      req.body,
      { new: true, runValidators: true }
    );

    if (!field) {
      sendError(res, `Canonical field with name '${req.params.id}' not found`, 404);
      return;
    }

    sendSuccess(res, field, 'Canonical field updated successfully');
  } catch (error: any) {
    sendError(res, error.message, 400);
  }
};

/**
 * Delete canonical field
 * DELETE /api/canonical-fields/:id
 */
export const deleteCanonicalField = async (req: Request, res: Response): Promise<void> => {
  try {
    const field = await CanonicalField.findOneAndDelete({ 
      field_name: req.params.id.toLowerCase() 
    });

    if (!field) {
      sendError(res, `Canonical field with name '${req.params.id}' not found`, 404);
      return;
    }

    sendSuccess(res, { message: 'Canonical field deleted successfully' });
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

/**
 * Toggle is_active status
 * PATCH /api/canonical-fields/:id/toggle
 */
export const toggleCanonicalField = async (req: Request, res: Response): Promise<void> => {
  try {
    const field = await CanonicalField.findOne({ 
      field_name: req.params.id.toLowerCase() 
    });

    if (!field) {
      sendError(res, `Canonical field with name '${req.params.id}' not found`, 404);
      return;
    }

    field.is_active = !field.is_active;
    await field.save();

    sendSuccess(res, field, `Canonical field ${field.is_active ? 'activated' : 'deactivated'} successfully`);
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

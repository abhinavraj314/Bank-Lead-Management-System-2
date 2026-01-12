import { Request, Response } from 'express';
import { Product } from '../models/Product';
import { Source } from '../models/Source';
import { productSchema, productUpdateSchema } from '../utils/validation';
import { sendSuccess, sendError, sendSuccessWithPagination, calculatePagination } from '../utils/responseHandler';

export const createProduct = async (req: Request, res: Response): Promise<void> => {
  try {
    const { error, value } = productSchema.validate(req.body);
    if (error) {
      sendError(res, error.details[0].message, 400);
      return;
    }
    
    // Check for duplicate p_id
    const existingProduct = await Product.findOne({ p_id: value.p_id });
    if (existingProduct) {
      sendError(res, `Product with p_id '${value.p_id}' already exists`, 409);
      return;
    }
    
    const product = await Product.create(value);
    sendSuccess(res, product, 'Product created successfully', 201);
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

export const getProducts = async (req: Request, res: Response): Promise<void> => {
  try {
    const page = Math.max(1, parseInt(req.query.page as string) || 1);
    const limit = Math.min(100, Math.max(1, parseInt(req.query.limit as string) || 10));
    const skip = (page - 1) * limit;

    const [products, total] = await Promise.all([
      Product.find().sort({ createdAt: -1 }).skip(skip).limit(limit),
      Product.countDocuments()
    ]);

    sendSuccessWithPagination(res, products, calculatePagination(page, limit, total));
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

export const getProductById = async (req: Request, res: Response): Promise<void> => {
  try {
    const product = await Product.findOne({ p_id: req.params.id.toUpperCase() });
    
    if (!product) {
      sendError(res, `Product with p_id '${req.params.id}' not found`, 404);
      return;
    }
    
    sendSuccess(res, product);
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

export const updateProduct = async (req: Request, res: Response): Promise<void> => {
  try {
    const { error, value } = productUpdateSchema.validate(req.body);
    if (error) {
      sendError(res, error.details[0].message, 400);
      return;
    }
    
    const product = await Product.findOneAndUpdate(
      { p_id: req.params.id.toUpperCase() },
      value,
      { new: true, runValidators: true }
    );
    
    if (!product) {
      sendError(res, `Product with p_id '${req.params.id}' not found`, 404);
      return;
    }
    
    sendSuccess(res, product, 'Product updated successfully');
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

export const deleteProduct = async (req: Request, res: Response): Promise<void> => {
  try {
    const p_id = req.params.id.toUpperCase();
    
    // Check if sources exist for this product
    const sourcesCount = await Source.countDocuments({ p_id });
    if (sourcesCount > 0) {
      sendError(res, `Cannot delete product: ${sourcesCount} source(s) are associated with this product`, 409);
      return;
    }
    
    const product = await Product.findOneAndDelete({ p_id });
    
    if (!product) {
      sendError(res, `Product with p_id '${req.params.id}' not found`, 404);
      return;
    }
    
    sendSuccess(res, { message: 'Product deleted successfully' });
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};





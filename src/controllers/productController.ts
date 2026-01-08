import { Request, Response } from 'express';
import { Product } from '../models/Product';
import { productSchema, productUpdateSchema } from '../utils/validation';

export const createProduct = async (req: Request, res: Response): Promise<void> => {
  try {
    const { error, value } = productSchema.validate(req.body);
    if (error) {
      res.status(400).json({ error: error.details[0].message });
      return;
    }
    
    // Check for duplicate p_id
    const existingProduct = await Product.findOne({ p_id: value.p_id });
    if (existingProduct) {
      res.status(409).json({
        error: `Product with p_id '${value.p_id}' already exists`
      });
      return;
    }
    
    const product = await Product.create(value);
    res.status(201).json(product);
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const getProducts = async (_req: Request, res: Response): Promise<void> => {
  try {
    const products = await Product.find().sort({ createdAt: -1 });
    res.json({
      count: products.length,
      products
    });
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const getProductById = async (req: Request, res: Response): Promise<void> => {
  try {
    const product = await Product.findOne({ p_id: req.params.id });
    
    if (!product) {
      res.status(404).json({
        error: `Product with p_id '${req.params.id}' not found`
      });
      return;
    }
    
    res.json(product);
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const updateProduct = async (req: Request, res: Response): Promise<void> => {
  try {
    const { error, value } = productUpdateSchema.validate(req.body);
    if (error) {
      res.status(400).json({ error: error.details[0].message });
      return;
    }
    
    const product = await Product.findOneAndUpdate(
      { p_id: req.params.id },
      value,
      { new: true, runValidators: true }
    );
    
    if (!product) {
      res.status(404).json({
        error: `Product with p_id '${req.params.id}' not found`
      });
      return;
    }
    
    res.json(product);
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const deleteProduct = async (req: Request, res: Response): Promise<void> => {
  try {
    const product = await Product.findOneAndDelete({ p_id: req.params.id });
    
    if (!product) {
      res.status(404).json({
        error: `Product with p_id '${req.params.id}' not found`
      });
      return;
    }
    
    res.json({
      message: 'Product deleted successfully',
      product
    });
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};





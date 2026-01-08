import { Request, Response } from 'express';
import { Source } from '../models/Source';
import { Product } from '../models/Product';
import { sourceSchema, sourceUpdateSchema } from '../utils/validation';

export const createSource = async (req: Request, res: Response): Promise<void> => {
  try {
    const { error, value } = sourceSchema.validate(req.body);
    if (error) {
      res.status(400).json({ error: error.details[0].message });
      return;
    }
    
    // Validate that product exists
    const product = await Product.findOne({ p_id: value.p_id });
    if (!product) {
      res.status(400).json({
        error: `Product with p_id '${value.p_id}' does not exist`
      });
      return;
    }
    
    // Check for duplicate source_id
    const existingSource = await Source.findOne({ source_id: value.source_id });
    if (existingSource) {
      res.status(409).json({
        error: `Source with source_id '${value.source_id}' already exists`
      });
      return;
    }
    
    const source = await Source.create(value);
    res.status(201).json(source);
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const getSources = async (req: Request, res: Response): Promise<void> => {
  try {
    const filter: any = {};
    
    // Filter by productId if provided
    if (req.query.productId) {
      filter.p_id = String(req.query.productId).toUpperCase();
    }
    
    const sources = await Source.find(filter).sort({ createdAt: -1 });
    
    res.json({
      count: sources.length,
      sources
    });
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const getSourceById = async (req: Request, res: Response): Promise<void> => {
  try {
    const source = await Source.findOne({ source_id: req.params.id });
    
    if (!source) {
      res.status(404).json({
        error: `Source with source_id '${req.params.id}' not found`
      });
      return;
    }
    
    res.json(source);
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const updateSource = async (req: Request, res: Response): Promise<void> => {
  try {
    const { error, value } = sourceUpdateSchema.validate(req.body);
    if (error) {
      res.status(400).json({ error: error.details[0].message });
      return;
    }
    
    // If updating p_id, validate the new product exists
    if (value.p_id) {
      const product = await Product.findOne({ p_id: value.p_id });
      if (!product) {
        res.status(400).json({
          error: `Product with p_id '${value.p_id}' does not exist`
        });
        return;
      }
    }
    
    const source = await Source.findOneAndUpdate(
      { source_id: req.params.id },
      value,
      { new: true, runValidators: true }
    );
    
    if (!source) {
      res.status(404).json({
        error: `Source with source_id '${req.params.id}' not found`
      });
      return;
    }
    
    res.json(source);
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const deleteSource = async (req: Request, res: Response): Promise<void> => {
  try {
    const source = await Source.findOneAndDelete({ source_id: req.params.id });
    
    if (!source) {
      res.status(404).json({
        error: `Source with source_id '${req.params.id}' not found`
      });
      return;
    }
    
    res.json({
      message: 'Source deleted successfully',
      source
    });
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};





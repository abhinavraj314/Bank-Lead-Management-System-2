import { Request, Response } from 'express';
import {
  getDeduplicationConfig,
  updateDeduplicationConfig,
  executeDeduplication,
  getDeduplicationStats
} from '../services/deduplicationService';
import { sendSuccess, sendError } from '../utils/responseHandler';

/**
 * Get current deduplication configuration
 * GET /api/deduplication/rules
 */
export const getDeduplicationRules = async (req: Request, res: Response): Promise<void> => {
  try {
    const config = getDeduplicationConfig();
    sendSuccess(res, config);
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

/**
 * Update deduplication configuration
 * PUT /api/deduplication/rules
 */
export const updateDeduplicationRules = async (req: Request, res: Response): Promise<void> => {
  try {
    const { useEmail, usePhone, useAadhar } = req.body;

    const config: any = {};
    
    if (typeof useEmail === 'boolean') {
      config.useEmail = useEmail;
    }
    if (typeof usePhone === 'boolean') {
      config.usePhone = usePhone;
    }
    if (typeof useAadhar === 'boolean') {
      config.useAadhar = useAadhar;
    }

    if (Object.keys(config).length === 0) {
      sendError(res, 'At least one configuration option must be provided', 400);
      return;
    }

    const updatedConfig = updateDeduplicationConfig(config);
    sendSuccess(res, updatedConfig, 'Deduplication rules updated successfully');
  } catch (error: any) {
    sendError(res, error.message, 400);
  }
};

/**
 * Execute deduplication
 * POST /api/deduplication/execute
 */
export const executeDeduplicationController = async (req: Request, res: Response): Promise<void> => {
  try {
    const { useEmail, usePhone, useAadhar } = req.body;

    const config: any = {};
    
    if (typeof useEmail === 'boolean') {
      config.useEmail = useEmail;
    }
    if (typeof usePhone === 'boolean') {
      config.usePhone = usePhone;
    }
    if (typeof useAadhar === 'boolean') {
      config.useAadhar = useAadhar;
    }

    const stats = await executeDeduplication(Object.keys(config).length > 0 ? config : undefined);
    sendSuccess(res, stats, 'Deduplication completed successfully');
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

/**
 * Get deduplication statistics
 * GET /api/deduplication/stats
 */
export const getDeduplicationStatsController = async (req: Request, res: Response): Promise<void> => {
  try {
    const stats = await getDeduplicationStats();
    sendSuccess(res, stats);
  } catch (error: any) {
    sendError(res, error.message, 500);
  }
};

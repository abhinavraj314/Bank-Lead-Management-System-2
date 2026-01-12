import { Request, Response, NextFunction } from 'express';
import { Error as MongooseError } from 'mongoose';

export interface ApiError extends Error {
  statusCode?: number;
  details?: any;
}

/**
 * Global error handler middleware
 * Handles all errors and returns consistent error responses
 */
export const errorHandler = (
  err: ApiError | MongooseError | Error,
  req: Request,
  res: Response,
  next: NextFunction
): void => {
  console.error('Error:', err);

  // Mongoose duplicate key error
  if ((err as any).code === 11000) {
    const field = Object.keys((err as any).keyPattern || {})[0];
    res.status(409).json({
      success: false,
      error: {
        message: `${field} already exists`,
        details: {
          field,
          value: (err as any).keyValue?.[field]
        }
      }
    });
    return;
  }

  // Mongoose validation error
  if (err instanceof MongooseError.ValidationError) {
    const errors: Record<string, string> = {};
    Object.keys(err.errors).forEach((key) => {
      errors[key] = err.errors[key].message;
    });
    res.status(400).json({
      success: false,
      error: {
        message: 'Validation error',
        details: errors
      }
    });
    return;
  }

  // Mongoose cast error (invalid ObjectId, etc.)
  if (err instanceof MongooseError.CastError) {
    res.status(400).json({
      success: false,
      error: {
        message: `Invalid ${err.path}: ${err.value}`,
        details: {
          path: err.path,
          value: err.value
        }
      }
    });
    return;
  }

  // Custom API error
  if ((err as ApiError).statusCode) {
    res.status((err as ApiError).statusCode!).json({
      success: false,
      error: {
        message: err.message,
        details: (err as ApiError).details
      }
    });
    return;
  }

  // Default 500 server error
  res.status(500).json({
    success: false,
    error: {
      message: process.env.NODE_ENV === 'production' 
        ? 'Internal server error' 
        : err.message,
      ...(process.env.NODE_ENV === 'development' && { stack: err.stack })
    }
  });
};

/**
 * 404 Not Found handler
 */
export const notFoundHandler = (req: Request, res: Response): void => {
  res.status(404).json({
    success: false,
    error: {
      message: `Route ${req.method} ${req.path} not found`
    }
  });
};

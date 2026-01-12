import { Response } from 'express';

export interface PaginationInfo {
  page: number;
  limit: number;
  total: number;
  pages: number;
}

export interface SuccessResponse<T = any> {
  success: true;
  data?: T;
  message?: string;
  pagination?: PaginationInfo;
}

export interface ErrorResponse {
  success: false;
  error: {
    message: string;
    details?: any;
  };
}

/**
 * Send success response
 */
export const sendSuccess = <T>(
  res: Response,
  data: T,
  message?: string,
  statusCode: number = 200
): void => {
  const response: SuccessResponse<T> = {
    success: true,
    data
  };

  if (message) {
    response.message = message;
  }

  res.status(statusCode).json(response);
};

/**
 * Send success response with pagination
 */
export const sendSuccessWithPagination = <T>(
  res: Response,
  data: T,
  pagination: PaginationInfo,
  statusCode: number = 200
): void => {
  const response: SuccessResponse<T> = {
    success: true,
    data,
    pagination
  };

  res.status(statusCode).json(response);
};

/**
 * Send error response
 */
export const sendError = (
  res: Response,
  message: string,
  statusCode: number = 500,
  details?: any
): void => {
  const response: ErrorResponse = {
    success: false,
    error: {
      message
    }
  };

  if (details) {
    response.error.details = details;
  }

  res.status(statusCode).json(response);
};

/**
 * Calculate pagination info
 */
export const calculatePagination = (
  page: number,
  limit: number,
  total: number
): PaginationInfo => {
  return {
    page,
    limit,
    total,
    pages: Math.ceil(total / limit)
  };
};

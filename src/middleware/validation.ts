import { Request, Response, NextFunction } from 'express';
import { body, param, query, validationResult, ValidationChain } from 'express-validator';

/**
 * Middleware to handle validation errors
 */
export const handleValidationErrors = (req: Request, res: Response, next: NextFunction): void => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    res.status(400).json({
      success: false,
      error: {
        message: 'Validation failed',
        details: errors.array()
      }
    });
    return;
  }
  next();
};

/**
 * Validate p_id format: 2-3 uppercase letters
 */
export const validateProductId = (): ValidationChain => {
  return param('id')
    .trim()
    .isLength({ min: 2, max: 20 })
    .matches(/^[A-Z0-9_]+$/)
    .withMessage('Product ID must be 2-20 characters and contain only uppercase letters, numbers, and underscores');
};

/**
 * Validate source_id format
 */
export const validateSourceId = (): ValidationChain => {
  return param('id')
    .trim()
    .isLength({ min: 2, max: 20 })
    .matches(/^[A-Z0-9_]+$/)
    .withMessage('Source ID must be 2-20 characters and contain only uppercase letters, numbers, and underscores');
};

/**
 * Validate lead_id format
 */
export const validateLeadId = (): ValidationChain => {
  return param('id')
    .trim()
    .isLength({ min: 1 })
    .withMessage('Lead ID is required');
};

/**
 * Validate email format
 */
export const validateEmail = (field: string = 'email'): ValidationChain => {
  return body(field)
    .optional()
    .trim()
    .isEmail()
    .normalizeEmail()
    .withMessage('Invalid email format');
};

/**
 * Validate phone format (10 digits)
 */
export const validatePhone = (field: string = 'phone_number'): ValidationChain => {
  return body(field)
    .optional()
    .trim()
    .matches(/^\d{10}$/)
    .withMessage('Phone number must be exactly 10 digits');
};

/**
 * Validate aadhar format (12 digits)
 */
export const validateAadhar = (): ValidationChain => {
  return body('aadhar_number')
    .optional()
    .trim()
    .matches(/^\d{12}$/)
    .withMessage('Aadhar number must be exactly 12 digits');
};

/**
 * Validate pagination query parameters
 */
export const validatePagination = (): ValidationChain[] => {
  return [
    query('page')
      .optional()
      .isInt({ min: 1 })
      .withMessage('Page must be a positive integer'),
    query('limit')
      .optional()
      .isInt({ min: 1, max: 100 })
      .withMessage('Limit must be between 1 and 100')
  ];
};

/**
 * Validate product creation
 */
export const validateProductCreate = (): ValidationChain[] => {
  return [
    body('p_id')
      .trim()
      .isLength({ min: 2, max: 20 })
      .matches(/^[A-Z0-9_]+$/)
      .withMessage('Product ID must be 2-20 characters and contain only uppercase letters, numbers, and underscores'),
    body('p_name')
      .trim()
      .isLength({ min: 1, max: 100 })
      .withMessage('Product name must be between 1 and 100 characters')
  ];
};

/**
 * Validate product update
 */
export const validateProductUpdate = (): ValidationChain[] => {
  return [
    body('p_name')
      .optional()
      .trim()
      .isLength({ min: 1, max: 100 })
      .withMessage('Product name must be between 1 and 100 characters')
  ];
};

/**
 * Validate source creation
 */
export const validateSourceCreate = (): ValidationChain[] => {
  return [
    body('source_id')
      .trim()
      .isLength({ min: 2, max: 20 })
      .matches(/^[A-Z0-9_]+$/)
      .withMessage('Source ID must be 2-20 characters and contain only uppercase letters, numbers, and underscores'),
    body('source_name')
      .trim()
      .isLength({ min: 1, max: 100 })
      .withMessage('Source name must be between 1 and 100 characters'),
    body('p_id')
      .trim()
      .isLength({ min: 1 })
      .withMessage('Product ID is required')
  ];
};

/**
 * Validate source update
 */
export const validateSourceUpdate = (): ValidationChain[] => {
  return [
    body('source_name')
      .optional()
      .trim()
      .isLength({ min: 1, max: 100 })
      .withMessage('Source name must be between 1 and 100 characters'),
    body('p_id')
      .optional()
      .trim()
      .isLength({ min: 1 })
      .withMessage('Product ID cannot be empty')
  ];
};

/**
 * Validate canonical field creation
 */
export const validateCanonicalFieldCreate = (): ValidationChain[] => {
  return [
    body('field_name')
      .trim()
      .isLength({ min: 1, max: 50 })
      .matches(/^[a-z][a-z0-9_]*$/)
      .withMessage('Field name must start with a letter and contain only lowercase letters, numbers, and underscores'),
    body('display_name')
      .trim()
      .isLength({ min: 1, max: 100 })
      .withMessage('Display name must be between 1 and 100 characters'),
    body('field_type')
      .isIn(['String', 'Number', 'Date', 'Boolean', 'Email', 'Phone'])
      .withMessage('Field type must be one of: String, Number, Date, Boolean, Email, Phone'),
    body('is_active')
      .optional()
      .isBoolean()
      .withMessage('is_active must be a boolean'),
    body('is_required')
      .optional()
      .isBoolean()
      .withMessage('is_required must be a boolean')
  ];
};

/**
 * Validate canonical field update
 */
export const validateCanonicalFieldUpdate = (): ValidationChain[] => {
  return [
    body('display_name')
      .optional()
      .trim()
      .isLength({ min: 1, max: 100 })
      .withMessage('Display name must be between 1 and 100 characters'),
    body('field_type')
      .optional()
      .isIn(['String', 'Number', 'Date', 'Boolean', 'Email', 'Phone'])
      .withMessage('Field type must be one of: String, Number, Date, Boolean, Email, Phone'),
    body('is_active')
      .optional()
      .isBoolean()
      .withMessage('is_active must be a boolean'),
    body('is_required')
      .optional()
      .isBoolean()
      .withMessage('is_required must be a boolean')
  ];
};

/**
 * Validate lead creation
 */
export const validateLeadCreate = (): ValidationChain[] => {
  return [
    body('name')
      .optional()
      .trim()
      .isLength({ max: 200 })
      .withMessage('Name must be less than 200 characters'),
    validatePhone('phone_number'),
    validateEmail('email'),
    validateAadhar(),
    body('source_id')
      .optional()
      .trim()
      .isLength({ min: 1 })
      .withMessage('Source ID cannot be empty'),
    body('p_id')
      .optional()
      .trim()
      .isLength({ min: 1 })
      .withMessage('Product ID cannot be empty')
  ];
};

/**
 * Validate lead update
 */
export const validateLeadUpdate = (): ValidationChain[] => {
  return [
    body('name')
      .optional()
      .trim()
      .isLength({ max: 200 })
      .withMessage('Name must be less than 200 characters'),
    validatePhone('phone_number'),
    validateEmail('email'),
    validateAadhar(),
    body('source_id')
      .optional()
      .trim()
      .isLength({ min: 1 })
      .withMessage('Source ID cannot be empty'),
    body('p_id')
      .optional()
      .trim()
      .isLength({ min: 1 })
      .withMessage('Product ID cannot be empty')
  ];
};

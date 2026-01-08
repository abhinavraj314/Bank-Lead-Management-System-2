import Joi from 'joi';

// Product validation
export const productSchema = Joi.object({
  p_id: Joi.string()
    .required()
    .trim()
    .uppercase()
    .min(2)
    .max(20)
    .pattern(/^[A-Z0-9_]+$/)
    .messages({
      'string.pattern.base': 'Product ID must contain only uppercase letters, numbers, and underscores',
      'string.empty': 'Product ID is required'
    }),
  p_name: Joi.string()
    .required()
    .trim()
    .min(2)
    .max(100)
    .messages({
      'string.empty': 'Product name is required'
    })
});

export const productUpdateSchema = Joi.object({
  p_name: Joi.string()
    .trim()
    .min(2)
    .max(100)
});

// Source validation
export const sourceSchema = Joi.object({
  source_id: Joi.string()
    .required()
    .trim()
    .uppercase()
    .min(2)
    .max(20)
    .pattern(/^[A-Z0-9_]+$/)
    .messages({
      'string.pattern.base': 'Source ID must contain only uppercase letters, numbers, and underscores',
      'string.empty': 'Source ID is required'
    }),
  source_name: Joi.string()
    .required()
    .trim()
    .min(2)
    .max(100)
    .messages({
      'string.empty': 'Source name is required'
    }),
  p_id: Joi.string()
    .required()
    .trim()
    .uppercase()
    .messages({
      'string.empty': 'Product ID is required'
    })
});

export const sourceUpdateSchema = Joi.object({
  source_name: Joi.string()
    .trim()
    .min(2)
    .max(100),
  p_id: Joi.string()
    .trim()
    .uppercase()
}).min(1);

// Lead upload validation
export const leadUploadSchema = Joi.object({
  p_id: Joi.string()
    .required()
    .trim()
    .uppercase(),
  source_id: Joi.string()
    .required()
    .trim()
    .uppercase()
});

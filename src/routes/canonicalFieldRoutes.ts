import { Router } from 'express';
import {
  getCanonicalFields,
  getCanonicalFieldById,
  createCanonicalField,
  updateCanonicalField,
  deleteCanonicalField,
  toggleCanonicalField
} from '../controllers/canonicalFieldController';
import {
  validateCanonicalFieldCreate,  // ← Make sure this matches validation.ts export
  validateCanonicalFieldUpdate,
  handleValidationErrors
} from '../middleware/validation';

const router = Router();

// GET /api/canonical-fields
router.get('/', getCanonicalFields);

// GET /api/canonical-fields/:id
router.get('/:id', getCanonicalFieldById);

// POST /api/canonical-fields ← THIS IS YOUR PROBLEM LINE
router.post('/', createCanonicalField);  // ← TEMPORARILY BYPASS VALIDATION

// PUT /api/canonical-fields/:id
router.put('/:id', validateCanonicalFieldUpdate(), handleValidationErrors, updateCanonicalField);

// DELETE /api/canonical-fields/:id
router.delete('/:id', deleteCanonicalField);

// PATCH /api/canonical-fields/:id/toggle
router.patch('/:id/toggle', toggleCanonicalField);

export default router;

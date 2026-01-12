import { Router } from 'express';
import {
  uploadMiddleware,
  uploadLeads,
  getLeads,
  getLeadById,
  createLead,
  updateLead,
  deleteLead,
  getLeadHistory,
  scoreLeadStub
} from '../controllers/leadController';
import {
  validateLeadCreate,
  validateLeadUpdate,
  validateLeadId,
  validatePagination,
  handleValidationErrors
} from '../middleware/validation';

const router = Router();

router.post('/upload', uploadMiddleware, uploadLeads);
router.get('/', validatePagination(), handleValidationErrors, getLeads);
router.post('/', validateLeadCreate(), handleValidationErrors, createLead);
router.get('/:id', validateLeadId(), handleValidationErrors, getLeadById);
router.put('/:id', validateLeadId(), validateLeadUpdate(), handleValidationErrors, updateLead);
router.delete('/:id', validateLeadId(), handleValidationErrors, deleteLead);
router.get('/:id/history', validateLeadId(), handleValidationErrors, getLeadHistory);
router.post('/:id/score', validateLeadId(), handleValidationErrors, scoreLeadStub);

export default router;





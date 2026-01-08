import { Router } from 'express';
import {
  uploadMiddleware,
  uploadLeads,
  getLeads,
  getLeadById,
  scoreLeadStub
} from '../controllers/leadController';

const router = Router();

router.post('/upload', uploadMiddleware, uploadLeads);
router.get('/', getLeads);
router.get('/:id', getLeadById);
router.post('/:id/score', scoreLeadStub);

export default router;





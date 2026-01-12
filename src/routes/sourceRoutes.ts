import { Router } from 'express';
import {
  createSource,
  getSources,
  getSourceById,
  updateSource,
  deleteSource
} from '../controllers/sourceController';
import {
  validateSourceCreate,
  validateSourceUpdate,
  validateSourceId,
  validatePagination,
  handleValidationErrors
} from '../middleware/validation';

const router = Router();

router.post('/', validateSourceCreate(), handleValidationErrors, createSource);
router.get('/', validatePagination(), handleValidationErrors, getSources);
router.get('/:id', validateSourceId(), handleValidationErrors, getSourceById);
router.put('/:id', validateSourceId(), validateSourceUpdate(), handleValidationErrors, updateSource);
router.delete('/:id', validateSourceId(), handleValidationErrors, deleteSource);

export default router;





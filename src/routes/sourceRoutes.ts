import { Router } from 'express';
import {
  createSource,
  getSources,
  getSourceById,
  updateSource,
  deleteSource
} from '../controllers/sourceController';

const router = Router();

router.post('/', createSource);
router.get('/', getSources);
router.get('/:id', getSourceById);
router.patch('/:id', updateSource);
router.delete('/:id', deleteSource);

export default router;





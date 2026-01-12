import { Router } from 'express';
import {
  getDeduplicationRules,
  updateDeduplicationRules,
  executeDeduplicationController,
  getDeduplicationStatsController
} from '../controllers/deduplicationController';

const router = Router();

router.get('/rules', getDeduplicationRules);
router.put('/rules', updateDeduplicationRules);
router.post('/execute', executeDeduplicationController);
router.get('/stats', getDeduplicationStatsController);

export default router;

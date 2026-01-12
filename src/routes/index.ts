import { Router } from 'express';
import productRoutes from './productRoutes';
import sourceRoutes from './sourceRoutes';
import canonicalFieldRoutes from './canonicalFieldRoutes';
import leadRoutes from './leadRoutes';
import deduplicationRoutes from './deduplicationRoutes';

const router = Router();

router.use('/products', productRoutes);
router.use('/sources', sourceRoutes);
router.use('/canonical-fields', canonicalFieldRoutes);
router.use('/leads', leadRoutes);
router.use('/deduplication', deduplicationRoutes);

export default router;

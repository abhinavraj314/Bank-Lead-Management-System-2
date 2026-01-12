import { Router } from 'express';
import {
  createProduct,
  getProducts,
  getProductById,
  updateProduct,
  deleteProduct
} from '../controllers/productController';
import {
  validateProductCreate,
  validateProductUpdate,
  validateProductId,
  validatePagination,
  handleValidationErrors
} from '../middleware/validation';

const router = Router();

router.post('/', validateProductCreate(), handleValidationErrors, createProduct);
router.get('/', validatePagination(), handleValidationErrors, getProducts);
router.get('/:id', validateProductId(), handleValidationErrors, getProductById);
router.put('/:id', validateProductId(), validateProductUpdate(), handleValidationErrors, updateProduct);
router.delete('/:id', validateProductId(), handleValidationErrors, deleteProduct);

export default router;





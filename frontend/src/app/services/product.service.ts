import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Product } from '../models/lead.models';

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  // Mock data - will be replaced with API calls later
  private mockProducts: Product[] = [
    { product_id: 'prod_001', product_name: 'Car Loan' },
    { product_id: 'prod_002', product_name: 'Home Loan' },
    { product_id: 'prod_003', product_name: 'Credit Card' },
    { product_id: 'prod_004', product_name: 'Personal Loan' },
    { product_id: 'prod_005', product_name: 'Business Loan' }
  ];

  /**
   * Get all products (mock service - returns Observable)
   * In production, this will call: GET /api/products
   */
  getProducts(): Observable<Product[]> {
    return of(this.mockProducts);
  }
}


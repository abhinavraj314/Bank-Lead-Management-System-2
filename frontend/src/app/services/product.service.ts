import { Injectable } from '@angular/core';
import { Observable, map, catchError, of } from 'rxjs';
import { Product, BackendProduct, ProductsResponse } from '../models/lead.models';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  constructor(private apiService: ApiService) {}

  /**
   * Get all products from backend
   * Maps backend response: { count, products } -> Product[]
   * Maps fields: p_id -> product_id, p_name -> product_name
   */
  getProducts(): Observable<Product[]> {
    return this.apiService.get<ProductsResponse>('/products').pipe(
      map((response) => {
        // Unwrap response if wrapped
        const products = response.products || (response as any);
        const productArray = Array.isArray(products) ? products : [];
        
        // Map backend fields to frontend fields
        return productArray.map((backendProduct: BackendProduct) => ({
          product_id: backendProduct.p_id,
          product_name: backendProduct.p_name
        }));
      }),
      catchError((error) => {
        console.error('Error fetching products:', error);
        // Return empty array on error to prevent UI breakage
        return of([]);
      })
    );
  }
}

import { Injectable } from '@angular/core';
import { Observable, map, catchError, of } from 'rxjs';
import { Product, BackendProduct, ApiResponse, Page } from '../models/lead.models';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root',
})
export class ProductService {
  constructor(private apiService: ApiService) {}

  /**
   * Get all products from Spring Boot backend
   * Handles ApiResponse<Page<Product>> wrapper
   * Maps camelCase fields (pId, pName) to frontend fields (product_id, product_name)
   */
  getProducts(): Observable<Product[]> {
    return this.apiService.get<ApiResponse<Page<BackendProduct>>>('/products').pipe(
      map((response) => {
        // Extract data from ApiResponse wrapper
        if (!response.success || !response.data) {
          console.warn('API returned unsuccessful response or no data:', response);
          return [];
        }

        // Extract products from Page wrapper
        const page = response.data;
        const products = page.content || [];

        // Map backend camelCase fields to frontend snake_case fields
        return products.map((backendProduct: BackendProduct) => ({
          product_id: backendProduct.pId,
          product_name: backendProduct.pName,
        }));
      }),
      catchError((error) => {
        console.error('Error fetching products:', error);
        // Return empty array on error to prevent UI breakage
        return of([]);
      }),
    );
  }

  /**
   * Create a new product
   * Requires p_id and p_name
   */
  createProduct(pId: string, pName: string): Observable<Product> {
    const requestBody = {
      p_id: pId.toUpperCase(),
      p_name: pName,
    };

    return this.apiService.post<ApiResponse<BackendProduct>>('/products', requestBody).pipe(
      map((response) => {
        if (!response.success || !response.data) {
          const errorMessage =
            response.error?.message || response.message || 'Failed to create product';
          throw new Error(errorMessage);
        }

        const backendProduct = response.data;
        return {
          product_id: backendProduct.pId,
          product_name: backendProduct.pName,
        };
      }),
      catchError((error) => {
        // Re-throw to let component handle errors
        throw error;
      }),
    );
  }

  /**
   * Delete a product by product_id
   */
  deleteProduct(productId: string): Observable<void> {
    return this.apiService.delete<ApiResponse<void>>(`/products/${productId.toUpperCase()}`).pipe(
      map((response) => {
        if (!response.success) {
          const errorMessage =
            response.error?.message || response.message || 'Failed to delete product';
          throw new Error(errorMessage);
        }
      }),
      catchError((error) => {
        throw error;
      }),
    );
  }
}

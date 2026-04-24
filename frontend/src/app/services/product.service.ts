import { Injectable } from '@angular/core';
import { Observable, map, catchError, of } from 'rxjs';
import {
  Product,
  BackendProduct,
  ApiResponse,
  Page,
  ProductRankingProfile,
} from '../models/lead.models';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root',
})
export class ProductService {
  constructor(private apiService: ApiService) {}

  getProductsPage(params?: {
    page?: number;
    limit?: number;
  }): Observable<{ items: Product[]; total: number; page: number; limit: number }> {
    const page = params?.page ?? 1;
    const limit = params?.limit ?? 10;
    return this.apiService
      .get<ApiResponse<Page<BackendProduct>>>(`/products?page=${page}&limit=${limit}`)
      .pipe(
        map((response) => {
          if (!response.success || !response.data) {
            console.warn('API returned unsuccessful response or no data:', response);
            return { items: [], total: 0, page, limit };
          }

          const pageData = response.data;
          const products = pageData.content || [];
          const items = products.map((backendProduct: BackendProduct) => ({
            product_id: backendProduct.pId,
            product_name: backendProduct.pName,
            deduplication_fields: backendProduct.deduplicationFields || [],
            created_date: backendProduct.createdAt
              ? new Date(backendProduct.createdAt).toISOString().split('T')[0]
              : undefined,
          }));

          return {
            items,
            total: Number(pageData.totalElements ?? items.length) || 0,
            page: Number(pageData.number ?? page - 1) + 1,
            limit: Number(pageData.size ?? limit) || limit,
          };
        }),
        catchError((error) => {
          console.error('Error fetching products:', error);
          return of({ items: [], total: 0, page, limit });
        }),
      );
  }

  /**
   * Get all products from Spring Boot backend
   * Handles ApiResponse<Page<Product>> wrapper
   * Maps camelCase fields (pId, pName) to frontend fields (product_id, product_name)
   */
  getProducts(): Observable<Product[]> {
    return this.getProductsPage({ page: 1, limit: 1000 }).pipe(map((result) => result.items));
  }

  /**
   * Create a new product
   * Backend auto-generates p_id if omitted.
   */
  createProduct(pName: string): Observable<Product> {
    const requestBody = {
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
          deduplication_fields: backendProduct.deduplicationFields || [],
          created_date: backendProduct.createdAt
            ? new Date(backendProduct.createdAt).toISOString().split('T')[0]
            : undefined,
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
  getRankingProfile(productId: string): Observable<ProductRankingProfile> {
    const id = productId.toUpperCase();
    return this.apiService.get<ApiResponse<ProductRankingProfile>>(`/products/${id}/ranking-profile`).pipe(
      map((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.error?.message || response.message || 'Failed to load ranking profile');
        }
        return response.data;
      }),
    );
  }

  saveRankingProfile(productId: string, profile: ProductRankingProfile): Observable<ProductRankingProfile> {
    const id = productId.toUpperCase();
    const body = { ...profile, pId: id };
    return this.apiService.put<ApiResponse<ProductRankingProfile>>(`/products/${id}/ranking-profile`, body).pipe(
      map((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.error?.message || response.message || 'Failed to save ranking profile');
        }
        return response.data;
      }),
    );
  }

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

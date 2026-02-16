import { Injectable } from '@angular/core';
import { Observable, map, catchError, of, throwError } from 'rxjs';
import { ApiService } from './api.service';
import {
  ApiResponse,
  ProductDeduplicationConfig,
  DeduplicationStats,
} from '../models/lead.models';

@Injectable({
  providedIn: 'root',
})
export class DeduplicationRulesService {
  constructor(private apiService: ApiService) {}

  /**
   * Get deduplication config for a product from backend
   * Returns canonical field names (email, phone_number, aadhar_number) used for deduplication
   */
  getRulesForProduct(productId: string): Observable<string[]> {
    const pId = productId?.toUpperCase() || '';
    if (!pId) return of([]);

    return this.apiService
      .get<ApiResponse<ProductDeduplicationConfig>>(
        `/deduplication/products/${encodeURIComponent(pId)}/config`
      )
      .pipe(
        map((response) => {
          if (!response.success || !response.data) return [];
          return response.data.deduplicationFields || [];
        }),
        catchError(() => of([]))
      );
  }

  /**
   * Save deduplication rules for a product to backend
   * @param productId Product ID
   * @param fieldNames Canonical field names: e.g. ["email", "phone_number", "aadhar_number"]
   */
  saveRulesForProduct(
    productId: string,
    fieldNames: string[]
  ): Observable<{ success: boolean }> {
    const pId = productId?.toUpperCase() || '';
    if (!pId) return throwError(() => new Error('Product ID is required'));

    return this.apiService
      .put<ApiResponse<unknown>>(
        `/deduplication/products/${encodeURIComponent(pId)}/config`,
        { deduplication_fields: fieldNames }
      )
      .pipe(
        map((response) => ({ success: !!response.success })),
        catchError((err) => throwError(() => err))
      );
  }

  /**
   * Execute global deduplication (uses legacy global config)
   * POST /api/deduplication/execute
   */
  executeDeduplication(config?: {
    useEmail?: boolean;
    usePhone?: boolean;
    useAadhar?: boolean;
  }): Observable<DeduplicationStats> {
    return this.apiService
      .post<ApiResponse<DeduplicationStats>>('/deduplication/execute', config ?? {})
      .pipe(
        map((response) => {
          if (!response.success || !response.data)
            throw new Error(response.message || 'Deduplication failed');
          return response.data;
        }),
        catchError((err) => throwError(() => err))
      );
  }

  /**
   * Execute deduplication for a single product using that product's config
   * POST /api/deduplication/execute/by-product?productId=X
   */
  executeDeduplicationForProduct(
    productId: string
  ): Observable<DeduplicationStats> {
    const pId = productId?.toUpperCase() || '';
    if (!pId) return throwError(() => new Error('Product ID is required'));

    return this.apiService
      .post<ApiResponse<DeduplicationStats>>(
        `/deduplication/execute/by-product?productId=${encodeURIComponent(pId)}`,
        {}
      )
      .pipe(
        map((response) => {
          if (!response.success || !response.data)
            throw new Error(response.message || 'Deduplication failed');
          return response.data;
        }),
        catchError((err) => throwError(() => err))
      );
  }

  /**
   * Execute deduplication for all products (each uses its own config)
   * POST /api/deduplication/execute/by-product/all
   */
  executeDeduplicationForAllProducts(): Observable<
    Record<string, DeduplicationStats>
  > {
    return this.apiService
      .post<ApiResponse<Record<string, DeduplicationStats>>>(
        '/deduplication/execute/by-product/all',
        {}
      )
      .pipe(
        map((response) => {
          if (!response.success || !response.data)
            throw new Error(response.message || 'Deduplication failed');
          return response.data;
        }),
        catchError((err) => throwError(() => err))
      );
  }

  /**
   * Clear rules for a product (client-side only; backend has persistent config)
   * No-op since we now load from backend.
   */
  clearRulesForProduct(_productId: string): void {
    // No longer needed - rules are stored in backend
  }

  /**
   * Legacy: Get all selected fields (not used with per-product config)
   */
  getSelectedFields(): Observable<string[]> {
    return of([]);
  }

  /**
   * Legacy: Save deduplication rules (use saveRulesForProduct instead)
   */
  saveDeduplicationRules(_fieldIds: string[]): Observable<{ success: boolean }> {
    return of({ success: true });
  }
}

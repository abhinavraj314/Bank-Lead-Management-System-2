import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { CanonicalField } from '../models/lead.models';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class DeduplicationRulesService {
  private readonly baseUrl = environment.apiUrl;

  // Product-scoped deduplication rules storage
  // Maps product_id -> array of canonical field IDs
  private productRulesMap: Map<string, string[]> = new Map();

  constructor(private http: HttpClient) {}

  /**
   * Get deduplication rules for a specific product
   * @param productId The product ID
   * @returns Observable of field IDs configured for this product
   */
  getRulesForProduct(productId: string): Observable<string[]> {
    const rules = this.productRulesMap.get(productId) || [];
    return of([...rules]);
  }

  /**
   * Save deduplication rules for a specific product
   * @param productId The product ID
   * @param fieldIds Array of canonical field IDs to use for deduplication
   * @returns Observable indicating success
   *
   * In production, this would POST to: /api/deduplication-rules?product_id={productId}
   */
  saveRulesForProduct(productId: string, fieldIds: string[]): Observable<{ success: boolean }> {
    this.productRulesMap.set(productId, [...fieldIds]);
    console.log(`Deduplication rules saved for product ${productId}:`, fieldIds);
    return of({ success: true });
  }

  /**
   * Execute deduplication on all leads
   * @returns Observable of deduplication statistics
   */
  executeDeduplication(): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/deduplication/execute`, {});
  }

  /**
   * Clear rules for a product (typically when user changes product selection)
   * @param productId The product ID
   */
  clearRulesForProduct(productId: string): void {
    this.productRulesMap.delete(productId);
  }

  /**
   * Legacy: Get all selected fields (for backwards compatibility if needed)
   * In production, this will call: GET /api/deduplication-rules
   */
  getSelectedFields(): Observable<string[]> {
    return of([]);
  }

  /**
   * Legacy: Save deduplication rules (for backwards compatibility if needed)
   * In production, this will call: POST /api/deduplication-rules
   */
  saveDeduplicationRules(fieldIds: string[]): Observable<{ success: boolean }> {
    return of({ success: true });
  }
}

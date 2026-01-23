import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { CanonicalField } from '../models/lead.models';

@Injectable({
  providedIn: 'root'
})
export class DeduplicationRulesService {
  // Mock data - will be replaced with API calls later
  private mockSelectedFields: string[] = ['field_001', 'field_002', 'field_003'];

  /**
   * Get selected deduplication fields (mock service - returns Observable)
   * In production, this will call: GET /api/deduplication-rules
   */
  getSelectedFields(): Observable<string[]> {
    return of([...this.mockSelectedFields]);
  }

  /**
   * Save deduplication rules (mock service - returns Observable)
   * In production, this will call: POST /api/deduplication-rules
   */
  saveDeduplicationRules(fieldIds: string[]): Observable<{ success: boolean }> {
    this.mockSelectedFields = [...fieldIds];
    return of({ success: true });
  }
}

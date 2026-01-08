import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Source } from '../models/lead.models';

@Injectable({
  providedIn: 'root'
})
export class SourceService {
  // Mock data - will be replaced with API calls later
  private mockSources: Source[] = [
    { source_id: 'src_001', source_name: 'Bank Website', product_id: 'prod_001' },
    { source_id: 'src_002', source_name: 'Partner', product_id: 'prod_002' },
    { source_id: 'src_003', source_name: 'Campaign', product_id: 'prod_003' },
    { source_id: 'src_004', source_name: 'Referral', product_id: 'prod_004' },
    { source_id: 'src_005', source_name: 'Social Media', product_id: 'prod_005' }
  ];

  /**
   * Get all sources (mock service - returns Observable)
   * In production, this will call: GET /api/sources
   */
  getSources(): Observable<Source[]> {
    return of(this.mockSources);
  }
}


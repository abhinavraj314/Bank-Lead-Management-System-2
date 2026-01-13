import { Injectable } from '@angular/core';
import { Observable, map, catchError, of } from 'rxjs';
import { Lead, BackendLead, LeadsResponse, UploadResponse } from '../models/lead.models';
import { ApiService } from './api.service';
import { ProductService } from './product.service';
import { SourceService } from './source.service';

@Injectable({
  providedIn: 'root'
})
export class LeadService {
  constructor(
    private apiService: ApiService,
    private productService: ProductService,
    private sourceService: SourceService
  ) {}

  /**
   * Get all leads from backend
   * Maps backend response: { leads, pagination } -> Lead[]
   * Maps fields: p_id -> product_id, phone_number -> phone
   * Note: product_name and source_name should be enriched by components using both services
   */
  getLeads(): Observable<Lead[]> {
    return this.apiService.get<LeadsResponse>('/leads').pipe(
      map((response) => {
        // Unwrap response if wrapped
        const leads = response.leads || (response as any);
        const leadArray = Array.isArray(leads) ? leads : [];
        
        // Map backend fields to frontend fields
        return leadArray.map((backendLead: BackendLead) => ({
          lead_id: backendLead.lead_id,
          name: backendLead.name,
          email: backendLead.email,
          phone: backendLead.phone_number || '',
          product_id: backendLead.p_id || '',
          product_name: '', // Components should enrich this using ProductService
          source_id: backendLead.source_id || '',
          source_name: '', // Components should enrich this using SourceService
          status: 'new' as const, // Default status since backend doesn't provide it
          created_at: this.formatDate(backendLead.created_at)
        }));
      }),
      catchError((error) => {
        console.error('Error fetching leads:', error);
        // Return empty array on error to prevent UI breakage
        return of([]);
      })
    );
  }

  /**
   * Upload leads file
   * Uses FormData with: file, p_id, source_id
   */
  uploadLeads(file: File, productId: string, sourceId: string): Observable<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('p_id', productId);
    formData.append('source_id', sourceId);

    return this.apiService.uploadFile<UploadResponse>('/leads/upload', formData).pipe(
      catchError((error) => {
        console.error('Error uploading leads:', error);
        throw error; // Re-throw to let component handle it
      })
    );
  }

  /**
   * Format date to string
   */
  private formatDate(date: string | Date | undefined): string {
    if (!date) return '';
    
    try {
      const d = typeof date === 'string' ? new Date(date) : date;
      return d.toISOString().split('T')[0]; // YYYY-MM-DD format
    } catch {
      return '';
    }
  }
}

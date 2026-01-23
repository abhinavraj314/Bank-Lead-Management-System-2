import { Injectable } from '@angular/core';
import { Observable, map, catchError, of } from 'rxjs';
import { Lead, BackendLead, ApiResponse, Page, UploadResponse } from '../models/lead.models';
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
   * Get all leads from Spring Boot backend
   * Handles ApiResponse<Page<Lead>> wrapper
   * Maps camelCase fields (leadId, phoneNumber, sourceId, pId) to frontend fields
   * Note: product_name and source_name should be enriched by components using both services
   */
  getLeads(): Observable<Lead[]> {
    return this.apiService.get<ApiResponse<Page<BackendLead>>>('/leads').pipe(
      map((response) => {
        // Extract data from ApiResponse wrapper
        if (!response.success || !response.data) {
          console.warn('API returned unsuccessful response or no data:', response);
          return [];
        }

        // Extract leads from Page wrapper
        const page = response.data;
        const leads = page.content || [];
        
        // Map backend camelCase fields to frontend snake_case fields
        return leads.map((backendLead: BackendLead) => ({
          lead_id: backendLead.leadId,
          name: backendLead.name,
          email: backendLead.email,
          phone: backendLead.phoneNumber || '',
          product_id: backendLead.pId || '',
          product_name: '', // Components should enrich this using ProductService
          source_id: backendLead.sourceId || '',
          source_name: '', // Components should enrich this using SourceService
          status: 'new' as const, // Default status since backend doesn't provide it
          created_at: this.formatDate(backendLead.createdAt)
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
   * Upload leads file to Spring Boot backend
   * Uses FormData with: file, p_id, source_id (matches Spring Boot @RequestParam names)
   * Handles ApiResponse<Map<String, Object>> wrapper
   */
  uploadLeads(file: File, productId: string, sourceId: string): Observable<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('p_id', productId);
    formData.append('source_id', sourceId);

    return this.apiService.uploadFile<ApiResponse<UploadResponse>>('/leads/upload', formData).pipe(
      map((response) => {
        // Extract data from ApiResponse wrapper
        if (!response.success || !response.data) {
          throw new Error(response.error?.message || response.message || 'Upload failed');
        }
        
        // Map Spring Boot response to UploadResponse format
        const data = response.data as any;
        return {
          totalRows: data.totalRows || 0,
          insertedCount: data.insertedCount || 0,
          mergedCount: data.mergedCount || 0,
          failedCount: data.failedCount || 0,
          failedRows: data.failedRows || []
        };
      }),
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

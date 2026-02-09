import { Injectable } from '@angular/core';
import { Observable, map, catchError, of } from 'rxjs';
import { Lead, BackendLead, ApiResponse, Page, UploadResponse } from '../models/lead.models';
import { ApiService } from './api.service';
import { ProductService } from './product.service';
import { SourceService } from './source.service';

@Injectable({
  providedIn: 'root',
})
export class LeadService {
  constructor(
    private apiService: ApiService,
    private productService: ProductService,
    private sourceService: SourceService,
  ) {}

  /**
   * Get all leads from Spring Boot backend
   * Backend now returns enriched leads with productName and sourceName
   * Handles ApiResponse<Page<LeadDTO>> wrapper
   * Backend LeadDTO uses camelCase: leadId, phoneNumber, productName, sourceName
   */
  getLeads(): Observable<Lead[]> {
    return this.apiService.get<ApiResponse<Page<any>>>('/leads').pipe(
      map((response) => {
        // Extract data from ApiResponse wrapper
        if (!response.success || !response.data) {
          console.warn('API returned unsuccessful response or no data:', response);
          return [];
        }

        // Extract leads from Page wrapper
        const page = response.data;
        const leads = page.content || [];

        // Map backend camelCase response to frontend snake_case fields
        // Backend LeadDTO returns: leadId, name, email, phoneNumber, aadharNumber, pId, productName, sourceId, sourceName, createdAt
        return leads.map((lead: any) => ({
          lead_id: lead.leadId || '',
          name: lead.name || '',
          email: lead.email || '',
          phone: lead.phoneNumber || '',
          product_id: lead.pId || '',
          product_name: lead.productName || '',
          source_id: lead.sourceId || '',
          source_name: lead.sourceName || '',
          status: 'new' as const,
          created_at: this.formatDate(lead.createdAt),
        }));
      }),
      catchError((error) => {
        console.error('Error fetching leads:', error);
        // Return empty array on error to prevent UI breakage
        return of([]);
      }),
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

    return this.apiService.uploadFile<ApiResponse<any>>('/leads/upload', formData).pipe(
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
          failedRows: data.failedRows || [],
          deduplication: data.deduplication || undefined,
        };
      }),
      catchError((error) => {
        console.error('Error uploading leads:', error);
        throw error; // Re-throw to let component handle it
      }),
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

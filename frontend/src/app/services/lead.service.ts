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
   * Get leads from Spring Boot backend. Uses limit=10000 by default to fetch all leads.
   */
  getLeads(params?: { page?: number; limit?: number; p_id?: string; source_id?: string; q?: string }): Observable<{ leads: Lead[]; total: number }> {
    const page = params?.page ?? 1;
    const limit = params?.limit ?? 10000;
    let url = `/leads?page=${page}&limit=${limit}`;
    if (params?.p_id) url += `&p_id=${encodeURIComponent(params.p_id)}`;
    if (params?.source_id) url += `&source_id=${encodeURIComponent(params.source_id)}`;
    if (params?.q?.trim()) url += `&q=${encodeURIComponent(params.q.trim())}`;
    return this.apiService.get<ApiResponse<Page<any>>>(url).pipe(
      map((response) => {
        // Extract data from ApiResponse wrapper
        if (!response.success || !response.data) {
          return { leads: [], total: 0 };
        }
        const pageData = response.data;
        const content = pageData.content || [];
        const total = pageData.totalElements ?? content.length;
        const leads: Lead[] = content.map((lead: any) => ({
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
        return { leads, total };
      }),
      catchError((error) => {
        console.error('Error fetching leads:', error);
        return of({ leads: [], total: 0 });
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

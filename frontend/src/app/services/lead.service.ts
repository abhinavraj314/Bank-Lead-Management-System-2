import { Injectable } from '@angular/core';
import { Observable, map, catchError, throwError, of } from 'rxjs';
import {
  Lead,
  BackendLead,
  ApiResponse,
  Page,
  UploadResponse,
  LeadHistoryData,
} from '../models/lead.models';
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
   * Get leads (server-side page, filters, sort). Default page size 25.
   */
  getLeads(params?: {
    page?: number;
    limit?: number;
    p_id?: string;
    source_id?: string;
    q?: string;
    status?: string;
    assigned_user_id?: string;
    assigned_to_me?: boolean;
    hide_terminal?: boolean;
    sort?: string;
    order?: string;
  }): Observable<{ leads: Lead[]; total: number }> {
    const page = params?.page ?? 1;
    const limit = params?.limit ?? 25;
    let url = `/leads?page=${page}&limit=${limit}`;
    if (params?.p_id) url += `&p_id=${encodeURIComponent(params.p_id)}`;
    if (params?.source_id) url += `&source_id=${encodeURIComponent(params.source_id)}`;
    if (params?.q?.trim()) url += `&q=${encodeURIComponent(params.q.trim())}`;
    if (params?.status) url += `&status=${encodeURIComponent(params.status)}`;
    if (params?.assigned_user_id)
      url += `&assigned_user_id=${encodeURIComponent(params.assigned_user_id)}`;
    if (params?.assigned_to_me) url += `&assigned_to_me=true`;
    if (params?.hide_terminal) url += `&hide_terminal=true`;
    if (params?.sort) url += `&sort=${encodeURIComponent(params.sort)}`;
    if (params?.order) url += `&order=${encodeURIComponent(params.order)}`;
    return this.apiService.get<ApiResponse<Page<any>>>(url).pipe(
      map((response) => {
        if (!response.success) {
          const message = response.error?.message || response.message || 'Failed to fetch leads';
          throw new Error(message);
        }

        // Support both Page wrapper (data.content, data.totalElements) and list (data[])
        const data: any = response.data;
        const content: any[] =
          data == null ? [] : Array.isArray(data) ? data : (data.content ?? []);
        const totalFromPage =
          data != null && !Array.isArray(data) ? (data.totalElements ?? data.total) : undefined;
        const totalFromPagination = response.pagination?.total;
        const total =
          totalFromPage ?? totalFromPagination ?? (Array.isArray(content) ? content.length : 0);

        const leads: Lead[] = (Array.isArray(content) ? content : []).map((lead: any) => {
          try {
            return {
              lead_id: lead.leadId ?? lead['lead_id'] ?? '',
              name: lead.name ?? '',
              email: lead.email ?? '',
              // Support phoneNumber, phNo, phone
              phone: lead.phoneNumber ?? lead['phNo'] ?? lead['phone'] ?? '',
              // Support pId, pid, product_id
              product_id: lead.pId ?? lead['pid'] ?? lead['product_id'] ?? '',
              product_name: lead.productName ?? lead['product_name'] ?? '',
              // Support sourceId, source_id
              source_id: lead.sourceId ?? lead['source_id'] ?? '',
              source_name: lead.sourceName ?? lead['source_name'] ?? '',
              // Support status, state (state is used in the user's data)
              status: ((lead.status ?? lead.state ?? '').toString().toUpperCase() ||
                'NEW') as Lead['status'],
              created_at: this.formatDate(lead.createdAt ?? lead['created_at']),
              lead_score:
                lead.leadScore != null
                  ? Number(lead.leadScore)
                  : lead['lead_score'] != null
                    ? Number(lead['lead_score'])
                    : null,
              score_reason: lead.scoreReason ?? lead['score_reason'] ?? null,
              assigned_user_id: lead.assignedUserId ?? lead['assigned_user_id'] ?? null,
              assigned_user_name: lead.assignedUserName ?? lead['assigned_user_name'] ?? null,
              allowed_next_states:
                lead.allowedNextStates ?? lead['allowed_next_states'] ?? undefined,
              team_id: lead.teamId ?? lead['team_id'] ?? null,
              score_breakdown: lead.scoreBreakdown ?? lead['score_breakdown'] ?? null,
            };
          } catch {
            return {
              lead_id: String(lead?.leadId ?? lead?.['lead_id'] ?? ''),
              name: '',
              email: '',
              phone: '',
              product_id: '',
              product_name: '',
              source_id: '',
              source_name: '',
              status: 'new' as const,
              created_at: '',
              lead_score: null,
              score_reason: null,
              assigned_user_id: null,
              assigned_user_name: null,
              allowed_next_states: undefined,
              team_id: null,
              score_breakdown: null,
            };
          }
        });
        return { leads, total: Number(total) || 0 };
      }),
      catchError((error) => {
        console.error('Error fetching leads:', error);
        return throwError(() => error);
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
   * Score all leads using ML model (batch scoring)
   */
  scoreAllLeads(): Observable<{ totalLeads: number; scoredCount: number }> {
    return this.apiService
      .post<ApiResponse<{ totalLeads: number; scoredCount: number }>>('/leads/score-all', {})
      .pipe(
        map((response) => {
          if (!response.success || !response.data) {
            throw new Error(response.message || 'Scoring failed');
          }
          return response.data;
        }),
      );
  }

  /**
   * Get ML service status
   */
  getMlStatus(): Observable<{ mlServiceAvailable: boolean; scoringMethod: string }> {
    return this.apiService
      .get<ApiResponse<{ mlServiceAvailable: boolean; scoringMethod: string }>>('/leads/ml-status')
      .pipe(
        map((response) => {
          if (!response.success || !response.data) {
            return { mlServiceAvailable: false, scoringMethod: 'Unknown' };
          }
          return response.data;
        }),
        catchError(() => of({ mlServiceAvailable: false, scoringMethod: 'Unavailable' })),
      );
  }

  getLeadHistory(leadId: string, page = 0, size = 100): Observable<LeadHistoryData> {
    return this.apiService
      .get<ApiResponse<LeadHistoryData>>(`/leads/${leadId}/history?page=${page}&size=${size}`)
      .pipe(
        map((response) => {
          if (!response.success || !response.data) {
            throw new Error(response.message || 'Failed to load lead history');
          }
          return response.data;
        }),
      );
  }

  /**
   * Update lead status (lifecycle state)
   */
  updateLeadStatus(
    leadId: string,
    status:
      | 'NEW'
      | 'ASSIGNED'
      | 'CONTACTED'
      | 'PROPOSAL_SENT'
      | 'IN_PROGRESS'
      | 'QUALIFIED'
      | 'CONVERTED'
      | 'NOT_CONVERTED'
      | 'CLOSED',
  ): Observable<Lead> {
    return this.apiService
      .patch<ApiResponse<BackendLead>>(`/leads/${leadId}/state`, { status })
      .pipe(
        map((response) => {
          if (!response.success || !response.data) {
            throw new Error(response.message || 'Failed to update status');
          }
          return this.mapBackendLeadToLead(response.data);
        }),
      );
  }

  /**
   * Update lead assignment
   */
  updateLeadAssignment(leadId: string, assignedUserId: string | null): Observable<Lead> {
    return this.apiService
      .patch<ApiResponse<BackendLead>>(`/leads/${leadId}/assignment`, { assignedUserId })
      .pipe(
        map((response) => {
          if (!response.success || !response.data) {
            throw new Error(response.message || 'Failed to update assignment');
          }
          return this.mapBackendLeadToLead(response.data);
        }),
      );
  }

  /**
   * Self-assign a lead (convenience method for sales users)
   */
  selfAssignLead(leadId: string): Observable<Lead> {
    return this.apiService
      .patch<ApiResponse<BackendLead>>(`/leads/${leadId}/assignment/self`, {})
      .pipe(
        map((response) => {
          if (!response.success || !response.data) {
            throw new Error(response.message || 'Failed to assign lead');
          }
          return this.mapBackendLeadToLead(response.data);
        }),
      );
  }

  /**
   * Update lead (status and/or assignment) - combined endpoint
   */
  updateLead(
    leadId: string,
    updates: {
      status?:
        | 'NEW'
        | 'ASSIGNED'
        | 'CONTACTED'
        | 'PROPOSAL_SENT'
        | 'IN_PROGRESS'
        | 'QUALIFIED'
        | 'CONVERTED'
        | 'NOT_CONVERTED'
        | 'CLOSED';
      assignedUserId?: string | null;
    },
  ): Observable<Lead> {
    return this.apiService.patch<ApiResponse<BackendLead>>(`/leads/${leadId}`, updates).pipe(
      map((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.message || 'Failed to update lead');
        }
        return this.mapBackendLeadToLead(response.data);
      }),
    );
  }

  /**
   * Create a new lead (individual creation, supports enriched data)
   */
  createLead(request: {
    name?: string;
    email?: string;
    phoneNumber?: string;
    aadharNumber?: string;
    pId: string;
    sourceId: string;
    income?: number | null;
    creditScore?: number | null;
    employmentType?: 'SALARIED' | 'SELF_EMPLOYED';
    loanAmount?: number | null;
  }): Observable<Lead> {
    return this.apiService.post<ApiResponse<BackendLead>>('/leads', request).pipe(
      map((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.message || 'Failed to create lead');
        }
        return this.mapBackendLeadToLead(response.data);
      }),
    );
  }

  /**
   * Map BackendLead to Lead
   */
  private mapBackendLeadToLead(lead: BackendLead): Lead {
    return {
      lead_id: lead.leadId ?? lead['lead_id'] ?? '',
      name: lead.name ?? '',
      email: lead.email ?? '',
      // Support phoneNumber, phNo, phone
      phone: lead.phoneNumber ?? lead['phNo'] ?? lead['phone'] ?? '',
      // Support pId, pid, product_id
      product_id: lead.pId ?? lead['pid'] ?? lead['product_id'] ?? '',
      product_name: lead['productName'] ?? lead['product_name'] ?? '',
      // Support sourceId, source_id
      source_id: lead.sourceId ?? lead['source_id'] ?? '',
      source_name: lead['sourceName'] ?? lead['source_name'] ?? '',
      // Support status, state
      status: ((lead.status ?? lead['state'] ?? '').toString().toUpperCase() ||
        'NEW') as Lead['status'],
      created_at: this.formatDate(lead.createdAt ?? lead['created_at']),
      lead_score:
        lead.leadScore != null
          ? Number(lead.leadScore)
          : lead['lead_score'] != null
            ? Number(lead['lead_score'])
            : null,
      score_reason: lead.scoreReason ?? lead['score_reason'] ?? null,
      assigned_user_id: lead.assignedUserId ?? lead['assigned_user_id'] ?? null,
      assigned_user_name: lead.assignedUserName ?? lead['assigned_user_name'] ?? null,
      allowed_next_states: lead.allowedNextStates ?? lead['allowed_next_states'] ?? undefined,
      team_id: lead.teamId ?? lead['team_id'] ?? null,
      score_breakdown: lead.scoreBreakdown ?? lead['score_breakdown'] ?? null,
    };
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

import { Injectable } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { ApiResponse } from '../models/lead.models';
import { ApiService } from './api.service';

export type TrendBucket = 'day' | 'week' | 'month';

export interface LeadSourceReportResponse {
  summary: {
    totalLeads: number;
    convertedLeads: number;
    notConvertedLeads: number;
    conversionRate: number;
  };
  sourceBreakdown: Array<{
    sourceId: string;
    sourceName: string;
    totalLeads: number;
    convertedLeads: number;
    notConvertedLeads: number;
    conversionRate: number;
  }>;
  stateDistribution: Array<{
    state: string;
    count: number;
  }>;
  trend: Array<{
    period: string;
    totalLeads: number;
    convertedLeads: number;
  }>;
}

@Injectable({
  providedIn: 'root',
})
export class ReportsService {
  constructor(private readonly apiService: ApiService) {}

  getLeadSourceAnalytics(params?: {
    productId?: string;
    sourceId?: string;
    from?: string;
    to?: string;
    bucket?: TrendBucket;
  }): Observable<LeadSourceReportResponse> {
    let endpoint = '/reports/lead-sources';
    const query = new URLSearchParams();
    if (params?.productId && params.productId !== 'ALL') {
      query.set('productId', params.productId);
    }
    if (params?.sourceId && params.sourceId !== 'ALL') {
      query.set('sourceId', params.sourceId);
    }
    if (params?.from) {
      query.set('from', params.from);
    }
    if (params?.to) {
      query.set('to', params.to);
    }
    if (params?.bucket) {
      query.set('bucket', params.bucket);
    }
    const queryString = query.toString();
    if (queryString) {
      endpoint = `${endpoint}?${queryString}`;
    }

    return this.apiService.get<ApiResponse<LeadSourceReportResponse>>(endpoint).pipe(
      map((response) => {
        if (!response.success || !response.data) {
          throw new Error(response.error?.message || response.message || 'Failed to load reports');
        }
        return response.data;
      }),
      catchError((error) => throwError(() => error)),
    );
  }
}

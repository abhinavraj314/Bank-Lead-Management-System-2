import { Injectable } from '@angular/core';
import { Observable, map, catchError, of } from 'rxjs';
import { Source, BackendSource, ApiResponse, Page } from '../models/lead.models';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root',
})
export class SourceService {
  constructor(private apiService: ApiService) {}

  getSourcesPage(params?: {
    page?: number;
    limit?: number;
  }): Observable<{ items: Source[]; total: number; page: number; limit: number }> {
    const page = params?.page ?? 1;
    const limit = params?.limit ?? 10;
    return this.apiService
      .get<ApiResponse<Page<BackendSource>>>(`/sources?page=${page}&limit=${limit}`)
      .pipe(
        map((response) => {
          if (!response.success || !response.data) {
            console.warn('API returned unsuccessful response or no data:', response);
            return { items: [], total: 0, page, limit };
          }

          const pageData = response.data;
          const sources = pageData.content || [];
          const items = sources.map((backendSource: BackendSource) => {
            const backendStatus = (backendSource as any).status;
            let normalizedStatus: 'active' | 'inactive' | undefined = undefined;

            if (backendStatus && typeof backendStatus === 'string') {
              const lowerStatus = backendStatus.toLowerCase();
              if (lowerStatus === 'active' || lowerStatus === 'inactive') {
                normalizedStatus = lowerStatus as 'active' | 'inactive';
              }
            }

            return {
              source_id: backendSource.sId,
              source_name: backendSource.sName,
              product_id: backendSource.pId,
              status: normalizedStatus,
              columns: backendSource.columns || [],
            };
          });

          return {
            items,
            total: Number(pageData.totalElements ?? items.length) || 0,
            page: Number(pageData.number ?? page - 1) + 1,
            limit: Number(pageData.size ?? limit) || limit,
          };
        }),
        catchError((error) => {
          console.error('Error fetching sources:', error);
          return of({ items: [], total: 0, page, limit });
        }),
      );
  }

  /**
   * Get all sources from Spring Boot backend
   * Handles ApiResponse<Page<Source>> wrapper
   * Maps camelCase fields (sId, sName, pId) to frontend fields (source_id, source_name, product_id)
   */
  getSources(): Observable<Source[]> {
    return this.getSourcesPage({ page: 1, limit: 1000 }).pipe(map((result) => result.items));
  }

  /**
   * Create a new source with columns metadata
   * Backend auto-generates s_id if not provided
   */
  createSource(source: {
    s_id?: string;
    s_name: string;
    p_id: string;
    columns?: string[];
  }): Observable<Source> {
    const requestBody: any = {
      s_name: source.s_name,
      p_id: source.p_id.toUpperCase(),
    };

    // Add s_id if explicitly provided
    if (source.s_id) {
      requestBody.s_id = source.s_id.toUpperCase();
    }

    // Add columns to request payload if provided
    if (source.columns && source.columns.length > 0) {
      requestBody.columns = source.columns;
    }

    return this.apiService.post<ApiResponse<BackendSource>>('/sources', requestBody).pipe(
      map((response) => {
        if (!response.success || !response.data) {
          const errorMessage =
            response.error?.message || response.message || 'Failed to create source';
          throw new Error(errorMessage);
        }

        const backendSource = response.data;
        return {
          source_id: backendSource.sId,
          source_name: backendSource.sName,
          product_id: backendSource.pId,
          status: 'active' as const,
        };
      }),
      catchError((error) => {
        // Re-throw to let component handle errors
        throw error;
      }),
    );
  }

  /**
   * Delete a source by source_id
   */
  deleteSource(sourceId: string): Observable<void> {
    return this.apiService.delete<ApiResponse<void>>(`/sources/${sourceId.toUpperCase()}`).pipe(
      map((response) => {
        if (!response.success) {
          const errorMessage =
            response.error?.message || response.message || 'Failed to delete source';
          throw new Error(errorMessage);
        }
      }),
      catchError((error) => {
        throw error;
      }),
    );
  }
}

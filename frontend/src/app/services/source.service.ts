import { Injectable } from '@angular/core';
import { Observable, map, catchError, of } from 'rxjs';
import { Source, BackendSource, SourcesResponse } from '../models/lead.models';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root'
})
export class SourceService {
  constructor(private apiService: ApiService) {}

  /**
   * Get all sources from backend
   * Maps backend response: { count, sources } -> Source[]
   * Maps fields: p_id -> product_id
   */
  getSources(): Observable<Source[]> {
    return this.apiService.get<SourcesResponse>('/sources').pipe(
      map((response) => {
        // Unwrap response if wrapped
        const sources = response.sources || (response as any);
        const sourceArray = Array.isArray(sources) ? sources : [];
        
        // Map backend fields to frontend fields
        return sourceArray.map((backendSource: BackendSource) => {
          // Normalize status if provided by backend, otherwise undefined
          const backendStatus = (backendSource as any).status;
          let normalizedStatus: 'active' | 'inactive' | undefined = undefined;
          
          if (backendStatus && typeof backendStatus === 'string') {
            const lowerStatus = backendStatus.toLowerCase();
            if (lowerStatus === 'active' || lowerStatus === 'inactive') {
              normalizedStatus = lowerStatus as 'active' | 'inactive';
            }
          }
          
          return {
            source_id: backendSource.source_id,
            source_name: backendSource.source_name,
            product_id: backendSource.p_id,
            status: normalizedStatus
          };
        });
      }),
      catchError((error) => {
        console.error('Error fetching sources:', error);
        // Return empty array on error to prevent UI breakage
        return of([]);
      })
    );
  }
}

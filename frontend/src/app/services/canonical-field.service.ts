import { Injectable } from '@angular/core';
import { Observable, map, catchError, of } from 'rxjs';
import { CanonicalField, BackendCanonicalField } from '../models/lead.models';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root'
})
export class CanonicalFieldService {
  constructor(private apiService: ApiService) {}

  /**
   * Get all canonical fields from backend
   * Maps backend response -> CanonicalField[]
   * Maps fields: field_type -> type, is_active -> status
   */
  getCanonicalFields(): Observable<CanonicalField[]> {
    return this.apiService.get<BackendCanonicalField[]>('/canonical-fields').pipe(
      map((response) => {
        // Handle both array and wrapped responses
        const fields = Array.isArray(response) 
          ? response 
          : (response as any).data || (response as any).fields || [];
        
        // Map backend fields to frontend fields
        return fields.map((backendField: BackendCanonicalField) => ({
          field_id: backendField._id || undefined,
          field_name: backendField.field_name,
          display_name: backendField.display_name,
          type: this.mapFieldType(backendField.field_type),
          version: backendField.version,
          status: backendField.is_active ? 'Active' : 'Inactive'
        }));
      }),
      catchError((error) => {
        console.error('Error fetching canonical fields:', error);
        // Return empty array on error to prevent UI breakage
        return of([]);
      })
    );
  }

  /**
   * Map backend field_type to frontend type
   */
  private mapFieldType(backendType: string): 'String' | 'Number' | 'Date' | 'Boolean' | 'Email' | 'Phone' {
    const typeMap: Record<string, 'String' | 'Number' | 'Date' | 'Boolean' | 'Email' | 'Phone'> = {
      'string': 'String',
      'number': 'Number',
      'date': 'Date',
      'boolean': 'Boolean',
      'email': 'Email',
      'phone': 'Phone'
    };
    
    return typeMap[backendType.toLowerCase()] || 'String';
  }
}

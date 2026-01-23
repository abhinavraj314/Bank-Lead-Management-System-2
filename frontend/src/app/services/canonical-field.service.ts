import { Injectable } from '@angular/core';
import { Observable, map, catchError, of, throwError } from 'rxjs';
import { CanonicalField, BackendCanonicalField, ApiResponse, Page } from '../models/lead.models';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root'
})
export class CanonicalFieldService {
  constructor(private apiService: ApiService) {}

  /**
   * Get all canonical fields from Spring Boot backend
   * Handles ApiResponse<Page<CanonicalField>> wrapper
   * Maps camelCase fields to frontend fields
   */
  getCanonicalFields(): Observable<CanonicalField[]> {
    return this.apiService.get<ApiResponse<Page<BackendCanonicalField>>>('/canonical-fields').pipe(
      map((response) => {
        // Extract data from ApiResponse wrapper
        if (!response.success || !response.data) {
          console.warn('API returned unsuccessful response or no data:', response);
          return [];
        }

        // Extract fields from Page wrapper
        const page = response.data;
        const fields = page.content || [];
        
        // Map backend camelCase fields to frontend fields
        return fields.map((backendField: BackendCanonicalField): CanonicalField => ({
          field_id: backendField.id || undefined,
          field_name: backendField.fieldName,
          display_name: backendField.displayName,
          type: this.mapFieldType(backendField.fieldType),
          version: parseInt(backendField.version) || 1,
          status: (backendField.isActive ? 'Active' : 'Inactive') as 'Active' | 'Inactive'
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
   * Create a new canonical field
   * Validates unique field_name and returns created field or error
   */
  createCanonicalField(field: {
    field_name: string;
    display_name: string;
    field_type: 'String' | 'Number' | 'Date' | 'Boolean';
    is_active?: boolean;
    is_required?: boolean;
    version?: string;
  }): Observable<CanonicalField> {
    const requestBody = {
      field_name: field.field_name.toLowerCase(),
      display_name: field.display_name,
      field_type: field.field_type,
      is_active: field.is_active !== undefined ? field.is_active : true,
      is_required: field.is_required !== undefined ? field.is_required : false,
      version: field.version || 'v1'
    };

    return this.apiService.post<ApiResponse<BackendCanonicalField>>('/canonical-fields', requestBody).pipe(
      map((response) => {
        if (!response.success || !response.data) {
          const errorMessage = response.error?.message || response.message || 'Failed to create canonical field';
          throw new Error(errorMessage);
        }

        const backendField = response.data;
        return {
          field_id: backendField.id || undefined,
          field_name: backendField.fieldName,
          display_name: backendField.displayName,
          type: this.mapFieldType(backendField.fieldType),
          version: parseInt(backendField.version) || 1,
          status: (backendField.isActive ? 'Active' : 'Inactive') as 'Active' | 'Inactive'
        } as CanonicalField;
      }),
      catchError((error) => {
        // Re-throw to let component handle validation errors
        if (error.status === 409 || error.message?.includes('already exists')) {
          throw new Error(`Field '${field.field_name}' already exists`);
        }
        throw error;
      })
    );
  }

  /**
   * Map backend field_type to frontend type
   */
  private mapFieldType(backendType: string): 'String' | 'Number' | 'Date' | 'Boolean' | 'Email' | 'Phone' {
    const typeMap: Record<string, 'String' | 'Number' | 'Date' | 'Boolean' | 'Email' | 'Phone'> = {
      'String': 'String',
      'Number': 'Number',
      'Date': 'Date',
      'Boolean': 'Boolean',
      'Email': 'Email',
      'Phone': 'Phone',
      'string': 'String',
      'number': 'Number',
      'date': 'Date',
      'boolean': 'Boolean',
      'email': 'Email',
      'phone': 'Phone'
    };
    
    return typeMap[backendType] || 'String';
  }
}

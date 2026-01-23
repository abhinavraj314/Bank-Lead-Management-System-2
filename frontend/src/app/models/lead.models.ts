// Frontend models (mapped from backend)
export interface Lead {
  lead_id: string;
  name?: string;
  email?: string;
  phone?: string;
  product_id: string;
  product_name?: string;
  source_id: string;
  source_name?: string;
  status: 'new' | 'contacted' | 'converted' | 'rejected';
  created_at: string;
}

export interface Product {
  product_id: string;
  product_name: string;
}

export interface Source {
  source_id: string;
  source_name: string;
  product_id: string;
  status?: 'active' | 'inactive';
}

export interface CanonicalField {
  field_id?: string;
  field_name: string;
  display_name: string;
  type: 'String' | 'Number' | 'Date' | 'Boolean' | 'Email' | 'Phone';
  version: number;
  status: 'Active' | 'Inactive';
}

export interface ProductWithStatus extends Product {
  status?: 'active' | 'inactive';
  created_date?: string;
}

// Backend response interfaces (Spring Boot camelCase format)
export interface BackendLead {
  id?: string; // MongoDB _id
  leadId: string; // Mapped to lead_id in Mongo
  name?: string;
  email?: string;
  phoneNumber?: string;
  aadharNumber?: string;
  sourceId?: string;
  pId?: string;
  createdAt?: string | Date;
  updatedAt?: string | Date;
  leadScore?: number | null;
  scoreReason?: string | null;
  // Additional fields that might be present
  [key: string]: any;
}

export interface BackendProduct {
  id?: string; // MongoDB _id
  pId: string; // Product ID (camelCase)
  pName: string; // Product Name (camelCase)
  createdAt?: string | Date;
  updatedAt?: string | Date;
}

export interface BackendSource {
  id?: string; // MongoDB _id
  sId: string; // Source ID (camelCase)
  sName: string; // Source Name (camelCase)
  pId: string; // Product ID (camelCase)
  columns?: string[]; // Source metadata columns
  createdAt?: string | Date;
  updatedAt?: string | Date;
}

export interface BackendCanonicalField {
  id?: string; // MongoDB _id
  fieldName: string; // camelCase
  displayName: string; // camelCase
  fieldType: string; // 'String' | 'Number' | 'Date' | 'Boolean'
  isActive: boolean; // camelCase
  isRequired: boolean; // camelCase
  version: string; // e.g., 'v1'
  createdAt?: string | Date;
  updatedAt?: string | Date;
}

// Spring Boot API Response Wrapper
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
  pagination?: {
    page: number;
    limit: number;
    total: number;
    pages: number;
  };
  error?: {
    message: string;
    details?: any;
  };
}

// Spring Boot Page Wrapper (for paginated responses)
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
}

// Upload Response (from Spring Boot)
export interface UploadResponse {
  totalRows: number;
  insertedCount: number;
  mergedCount: number;
  failedCount: number;
  failedRows?: Array<{
    rowNumber: number;
    reason: string;
    rawInput?: any;
  }>;
}

// Legacy interfaces (kept for backward compatibility if needed)
export interface ProductsResponse {
  count?: number;
  products: BackendProduct[];
}

export interface SourcesResponse {
  count?: number;
  sources: BackendSource[];
}

export interface LeadsResponse {
  leads: BackendLead[];
  pagination?: {
    page: number;
    limit: number;
    total: number;
    totalPages: number;
  };
}

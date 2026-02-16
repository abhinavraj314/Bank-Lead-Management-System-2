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
  deduplication_fields?: string[]; // Canonical field names: e.g. ["email", "phone_number", "aadhar_number"]
}

export interface Source {
  source_id: string;
  source_name: string;
  product_id: string;
  status?: 'active' | 'inactive';
  columns?: string[];
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
  deduplicationFields?: string[]; // Canonical field names for lead deduplication
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
  deduplication?: {
    totalLeadsBefore: number; // Total leads before deduplication
    duplicatesFound: number; // Number of duplicate leads found
    mergedCount: number; // Number of leads that were merged
    finalLeadCount: number; // Final lead count after deduplication
    error?: string;
  };
}

// Product deduplication config (per-product canonical fields)
export interface ProductDeduplicationConfig {
  pId: string;
  pName: string;
  deduplicationFields: string[];
  resolvedConfig: { useEmail: boolean; usePhone: boolean; useAadhar: boolean };
}

// Deduplication execute response
export interface DeduplicationStats {
  totalLeads: number;
  duplicatesFound: number;
  mergedCount: number;
  finalCount: number;
  mergeDetails?: Array<{ keptLeadId: string; mergedLeadIds: string[] }>;
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

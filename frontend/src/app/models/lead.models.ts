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

// Backend response interfaces (raw API responses)
export interface BackendLead {
  lead_id: string;
  name?: string;
  email?: string;
  phone_number?: string;
  aadhar_number?: string;
  source_id?: string;
  p_id?: string;
  created_at: string | Date;
  _id?: string;
  __v?: number;
  lead_score?: number | null;
  score_reason?: string | null;
  createdAt?: Date;
  updatedAt?: Date;
}

export interface BackendProduct {
  p_id: string;
  p_name: string;
  _id?: string;
  __v?: number;
  createdAt?: Date;
  updatedAt?: Date;
}

export interface BackendSource {
  source_id: string;
  source_name: string;
  p_id: string;
  _id?: string;
  __v?: number;
  createdAt?: Date;
  updatedAt?: Date;
}

export interface BackendCanonicalField {
  field_name: string;
  display_name: string;
  field_type: string;
  is_active: boolean;
  is_required: boolean;
  version: number;
  _id?: string;
  __v?: number;
}

// API response wrappers
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

export interface UploadResponse {
  success: boolean;
  totalRows: number;
  insertedCount: number;
  mergedCount: number;
  failedCount: number;
  failedRows?: Array<{
    index: number;
    reason: string;
    raw: any;
  }>;
}

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
  status:
    | 'NEW'
    | 'ASSIGNED'
    | 'CONTACTED'
    | 'PROPOSAL_SENT'
    | 'IN_PROGRESS'
    | 'QUALIFIED'
    | 'CONVERTED'
    | 'NOT_CONVERTED'
    | 'CLOSED'
    | 'new'
    | 'contacted'
    | 'converted'
    | 'rejected';
  /** From backend workflow — valid next states (+ current) for dropdown */
  allowed_next_states?: string[];
  team_id?: string | null;
  created_at: string;
  lead_score?: number | null;
  score_reason?: string | null;
  score_breakdown?: Record<string, unknown> | null;
  assigned_user_id?: string | null;
  assigned_user_name?: string | null;
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
  /** Primary workflow state (mirrors status when present) */
  state?:
    | 'NEW'
    | 'ASSIGNED'
    | 'CONTACTED'
    | 'PROPOSAL_SENT'
    | 'IN_PROGRESS'
    | 'QUALIFIED'
    | 'CONVERTED'
    | 'NOT_CONVERTED'
    | 'CLOSED';
  allowedNextStates?: string[];
  teamId?: string | null;
  scoreBreakdown?: Record<string, unknown> | null;
  assignedUserId?: string | null;
  assignedUserName?: string | null;
  statusUpdatedAt?: string | Date;
  assignedAt?: string | Date;
  // Additional fields that might be present
  [key: string]: any;
}

/** Per-product ML feature selection and post-ML ranking rules (backend: ProductRankingProfile) */
export interface ProductRankingRule {
  field: string;
  operator: string;
  value?: string | null;
  weightDelta: number;
  maxBoost?: number | null;
}

export interface ProductRankingProfile {
  id?: string;
  pId: string;
  canonicalFields?: string[]; // Selected ML features for this product
  rules?: ProductRankingRule[]; // Post-ML ranking adjustment rules
  updatedAt?: string | null;
}

export interface BackendProduct {
  id?: string; // MongoDB _id
  pId: string; // Product ID (camelCase)
  pName: string; // Product Name (camelCase)
  teamId?: string; // ID of the team associated with this product
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

/** Lead audit timeline (GET /api/leads/{id}/history) */
export interface LeadHistoryEvent {
  id?: string;
  leadId?: string;
  type?: string;
  at?: string;
  actorUserId?: string | null;
  payload?: Record<string, unknown>;
}

export interface LeadHistoryData {
  leadId?: string;
  events?: LeadHistoryEvent[];
  totalElements?: number;
  merged_from?: string[];
  sources_seen?: string[];
  products_seen?: string[];
  created_at?: string;
}

export interface TeamDto {
  id?: string;
  name: string;
  adminUserId: string;
  memberUserIds: string[];
  roundRobinIndex?: number;
}

export interface AssignmentRuleDto {
  id?: string;
  priority: number;
  productId?: string | null;
  sourceId?: string | null;
  teamId: string;
  assignedUserId?: string | null;
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

export interface UserResponse {
  id: string;
  userCode?: string;
  username: string;
  email: string;
  role: 'USER' | 'ADMIN';
  accountStatus: 'ACTIVE' | 'INVITED';
  createdAt?: string | Date;
  updatedAt?: string | Date;
}

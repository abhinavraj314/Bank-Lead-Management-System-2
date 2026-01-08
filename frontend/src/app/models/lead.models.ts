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
}


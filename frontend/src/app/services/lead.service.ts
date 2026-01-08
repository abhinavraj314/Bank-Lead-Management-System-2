import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Lead } from '../models/lead.models';

@Injectable({
  providedIn: 'root'
})
export class LeadService {
  // Mock data - will be replaced with API calls later
  private mockLeads: Lead[] = [
    {
      lead_id: 'lead_001',
      name: 'John Doe',
      email: 'john.doe@example.com',
      phone: '+1 234-567-8901',
      product_id: 'prod_001',
      product_name: 'Car Loan',
      source_id: 'src_001',
      source_name: 'Bank Website',
      status: 'new',
      created_at: '2024-01-15'
    },
    {
      lead_id: 'lead_002',
      name: 'Jane Smith',
      email: 'jane.smith@example.com',
      phone: '+1 234-567-8902',
      product_id: 'prod_002',
      product_name: 'Home Loan',
      source_id: 'src_002',
      source_name: 'Partner',
      status: 'contacted',
      created_at: '2024-01-14'
    },
    {
      lead_id: 'lead_003',
      name: 'Robert Johnson',
      email: 'robert.j@example.com',
      phone: '+1 234-567-8903',
      product_id: 'prod_003',
      product_name: 'Credit Card',
      source_id: 'src_003',
      source_name: 'Campaign',
      status: 'converted',
      created_at: '2024-01-13'
    },
    {
      lead_id: 'lead_004',
      name: 'Emily Davis',
      email: 'emily.davis@example.com',
      phone: '+1 234-567-8904',
      product_id: 'prod_001',
      product_name: 'Car Loan',
      source_id: 'src_001',
      source_name: 'Bank Website',
      status: 'new',
      created_at: '2024-01-12'
    },
    {
      lead_id: 'lead_005',
      name: 'Michael Wilson',
      email: 'michael.w@example.com',
      phone: '+1 234-567-8905',
      product_id: 'prod_002',
      product_name: 'Home Loan',
      source_id: 'src_002',
      source_name: 'Partner',
      status: 'contacted',
      created_at: '2024-01-11'
    },
    {
      lead_id: 'lead_006',
      name: 'Sarah Brown',
      email: 'sarah.brown@example.com',
      phone: '+1 234-567-8906',
      product_id: 'prod_003',
      product_name: 'Credit Card',
      source_id: 'src_003',
      source_name: 'Campaign',
      status: 'new',
      created_at: '2024-01-10'
    },
    {
      lead_id: 'lead_007',
      name: 'David Miller',
      email: 'david.m@example.com',
      phone: '+1 234-567-8907',
      product_id: 'prod_001',
      product_name: 'Car Loan',
      source_id: 'src_001',
      source_name: 'Bank Website',
      status: 'converted',
      created_at: '2024-01-09'
    },
    {
      lead_id: 'lead_008',
      name: 'Lisa Anderson',
      email: 'lisa.a@example.com',
      phone: '+1 234-567-8908',
      product_id: 'prod_002',
      product_name: 'Home Loan',
      source_id: 'src_002',
      source_name: 'Partner',
      status: 'new',
      created_at: '2024-01-08'
    }
  ];

  /**
   * Get all leads (mock service - returns Observable)
   * In production, this will call: GET /api/leads
   */
  getLeads(): Observable<Lead[]> {
    return of(this.mockLeads);
  }
}


import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard {
  private apiService = inject(ApiService);

  private readonly adminActionCards = [
    {
      title: 'Create / Upload Leads',
      description: 'Upload leads and view lead list',
      route: '/admin/leads',
      icon: 'leads',
      gradient: 'linear-gradient(135deg, #22c55e 0%, #16a34a 100%)'
    },
    {
      title: 'Create Canonical Field',
      description: 'Define new canonical fields for lead data standardization',
      route: '/admin/canonical-fields',
      icon: 'fields',
      gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
    },
    {
      title: 'Create Product',
      description: 'Add a new bank product or service offering',
      route: '/admin/products',
      icon: 'products',
      gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)'
    },
    {
      title: 'Create Source',
      description: 'Register a new lead source channel',
      route: '/admin/sources',
      icon: 'sources',
      gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)'
    }
  ];

  private readonly userActionCards = [
    {
      title: 'View Leads',
      description: 'View lead list',
      route: '/admin/leads',
      icon: 'leads',
      gradient: 'linear-gradient(135deg, #22c55e 0%, #16a34a 100%)'
    },
    {
      title: 'View Canonical Fields',
      description: 'View canonical fields for lead data standardization',
      route: '/admin/canonical-fields',
      icon: 'fields',
      gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
    },
    {
      title: 'View Products',
      description: 'Browse bank products and services',
      route: '/admin/products',
      icon: 'products',
      gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)'
    },
    {
      title: 'View Sources',
      description: 'Browse lead source channels',
      route: '/admin/sources',
      icon: 'sources',
      gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)'
    }
  ];

  isAdmin(): boolean {
    return this.apiService.isAdmin();
  }

  get actionCards() {
    return this.isAdmin() ? this.adminActionCards : this.userActionCards;
  }
}

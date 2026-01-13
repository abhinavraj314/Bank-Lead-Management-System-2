import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard {
  readonly actionCards = [
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
}

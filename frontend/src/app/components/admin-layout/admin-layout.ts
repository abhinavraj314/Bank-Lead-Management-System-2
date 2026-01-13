import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-admin-layout',
  imports: [CommonModule, RouterModule, RouterOutlet],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.css'
})
export class AdminLayout {
  protected readonly navItems = [
    { path: '/admin/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { path: '/admin/leads', label: 'Leads', icon: 'leads' },
    { path: '/admin/canonical-fields', label: 'Canonical Fields', icon: 'fields' },
    { path: '/admin/products', label: 'Products', icon: 'products' },
    { path: '/admin/sources', label: 'Sources', icon: 'sources' },
    { path: '/admin/deduplication-rules', label: 'Deduplication Rules', icon: 'rules' },
    { path: '/admin/ranking-config', label: 'Ranking Config', icon: 'ranking' }
  ];
}

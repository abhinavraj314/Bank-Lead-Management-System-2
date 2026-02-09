import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule, RouterOutlet } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-admin-layout',
  imports: [CommonModule, RouterModule, RouterOutlet],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.css',
})
export class AdminLayout {
  protected readonly adminNavItems = [
    { path: '/admin/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { path: '/admin/leads', label: 'Leads', icon: 'leads' },
    { path: '/admin/canonical-fields', label: 'Canonical Fields', icon: 'fields' },
    { path: '/admin/products', label: 'Products', icon: 'products' },
    { path: '/admin/sources', label: 'Sources', icon: 'sources' },
    { path: '/admin/deduplication-rules', label: 'Deduplication Rules', icon: 'rules' },
    { path: '/admin/ranking-config', label: 'Ranking Config', icon: 'ranking' },
  ];

  protected readonly userNavItems = [
    { path: '/admin/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { path: '/admin/leads', label: 'View Leads', icon: 'leads' },
    { path: '/admin/canonical-fields', label: 'View Canonical Fields', icon: 'fields' },
    { path: '/admin/products', label: 'View Products', icon: 'products' },
    { path: '/admin/sources', label: 'View Sources', icon: 'sources' },
  ];

  get navItems() {
    return this.isAdmin ? this.adminNavItems : this.userNavItems;
  }

  currentUser: any = null;
  sidebarOpen = true;

  constructor(
    private apiService: ApiService,
    private router: Router,
  ) {
    this.currentUser = this.apiService.getCurrentUser();
  }

  toggleSidebar() {
    this.sidebarOpen = !this.sidebarOpen;
  }

  logout() {
    if (confirm('Are you sure you want to logout?')) {
      this.apiService.logout();
      this.router.navigate(['/auth']);
    }
  }

  get isAdmin(): boolean {
    return this.apiService.isAdmin();
  }
}

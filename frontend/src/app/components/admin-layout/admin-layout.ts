import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule, RouterOutlet } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { ToastContainerComponent } from '../toast-container/toast-container';

@Component({
  selector: 'app-admin-layout',
  imports: [CommonModule, RouterModule, RouterOutlet, ToastContainerComponent],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.css',
})
export class AdminLayout {
  protected readonly adminNavItems = [
    { path: '/admin/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { path: '/admin/leads', label: 'Leads', icon: 'leads' },
    { path: '/admin/users', label: 'Users', icon: 'users' },
    { path: '/admin/teams', label: 'Teams', icon: 'teams' },
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
  showLogoutConfirm = signal(false);

  constructor(
    private apiService: ApiService,
    private router: Router,
    private toast: ToastService,
  ) {
    this.currentUser = this.apiService.getCurrentUser();
  }

  toggleSidebar() {
    this.sidebarOpen = !this.sidebarOpen;
  }

  logout() {
    this.showLogoutConfirm.set(true);
  }

  confirmLogout() {
    this.showLogoutConfirm.set(false);
    this.apiService.logout();
    this.router.navigate(['/auth']);
    this.toast.success('You have been logged out.');
  }

  cancelLogout() {
    this.showLogoutConfirm.set(false);
  }

  get isAdmin(): boolean {
    return this.apiService.isAdmin();
  }
}

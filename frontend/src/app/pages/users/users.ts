import { Component, OnInit, signal, inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';

type UserRow = {
  id: string;
  username: string;
  email: string;
  role: 'USER' | 'ADMIN' | string;
  accountStatus?: 'ACTIVE' | 'INVITED' | string;
};

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './users.html',
  styleUrl: './users.css',
})
export class UsersPage implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly users = signal<UserRow[]>([]);
  protected readonly loading = signal(false);
  protected readonly updatingUserId = signal<string | null>(null);
  protected readonly deletingUserId = signal<string | null>(null);

  // Simple "create user" form (not email invitation).
  protected createForm: {
    username: string;
    email: string;
    password: string;
    role: 'USER' | 'ADMIN';
  } = {
    username: '',
    email: '',
    password: '',
    role: 'USER',
  };

  protected readonly isCreating = signal(false);
  protected readonly showCreateUserModal = signal(false);
  protected readonly errorMessage = signal('');

  // Invitation form (email invite) - Option B
  protected inviteEmail = '';
  protected inviteRole: 'USER' | 'ADMIN' = 'USER';
  protected readonly isInviting = signal(false);

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    if (!this.isAdmin()) return;
    this.loadUsers();
  }

  isAdmin(): boolean {
    return this.apiService.isAdmin();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.apiService.getUsers(1, 1000).subscribe({
      next: (response: any) => {
        const content = response.data?.content || response.data || [];
        this.users.set(
          content.map((u: any) => ({
            id: u.id || u.userId || '',
            username: u.username || '',
            email: u.email || '',
            role: u.role || 'USER',
            accountStatus: u.accountStatus || 'ACTIVE',
          })),
        );
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        const msg = err?.message || err?.error?.message || 'Failed to load users';
        this.toast.error(msg);
      },
    });
  }

  updateRole(userId: string, newRole: 'USER' | 'ADMIN'): void {
    this.updatingUserId.set(userId);
    this.apiService.updateUser(userId, { role: newRole }).subscribe({
      next: () => {
        this.updatingUserId.set(null);
        this.toast.success('User role updated');
        this.loadUsers();
      },
      error: (err) => {
        this.updatingUserId.set(null);
        const msg = err?.message || err?.error?.message || 'Failed to update role';
        this.toast.error(msg);
      },
    });
  }

  deleteUser(userId: string): void {
    if (!confirm('Delete this user? This cannot be undone.')) return;
    this.deletingUserId.set(userId);
    this.apiService.deleteUser(userId).subscribe({
      next: () => {
        this.deletingUserId.set(null);
        this.toast.success('User deleted');
        this.loadUsers();
      },
      error: (err) => {
        this.deletingUserId.set(null);
        const msg = err?.message || err?.error?.message || 'Failed to delete user';
        this.toast.error(msg);
      },
    });
  }

  createUser(): void {
    if (!this.validateCreateForm()) {
      this.toast.error(this.errorMessage());
      return;
    }

    this.errorMessage.set('');
    this.isCreating.set(true);
    this.toast.info('Creating user...');

    this.apiService
      .createUser({
        username: this.createForm.username.trim(),
        email: this.createForm.email.trim(),
        password: this.createForm.password,
        role: this.createForm.role,
      })
      .subscribe({
        next: () => {
          this.isCreating.set(false);
          this.toast.success('User created');
          this.closeCreateUserModal();
          this.loadUsers();
        },
        error: (err) => {
          this.isCreating.set(false);
          const msg = err?.message || err?.error?.message || 'Failed to create user';
          this.toast.error(msg);
        },
      });
  }

  inviteByEmail(): void {
    const email = this.inviteEmail.trim();
    if (!email) return this.toast.error('Email is required');
    // basic email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) return this.toast.error('Invalid email format');

    this.isInviting.set(true);
    this.apiService
      .inviteUserByEmail({ email, role: this.inviteRole })
      .subscribe({
        next: (response: any) => {
          this.isInviting.set(false);
          const token = response?.data?.token ?? response?.token;
          const msg = 'Invitation created. Check the email (or use the link shown in the toast).';
          this.toast.success(msg);

          // For dev/testing: show accept link when we have token.
          if (token && isPlatformBrowser(this.platformId)) {
            const link = window.location.origin + `/auth/accept-invite?token=${encodeURIComponent(token)}`;
            this.toast.info('Accept link: ' + link, 8000);
          }

          this.inviteEmail = '';
        },
        error: (err) => {
          this.isInviting.set(false);
          const msg = err?.message || err?.error?.message || 'Failed to create invitation';
          this.toast.error(msg);
        },
      });
  }

  openCreateUserModal(): void {
    this.showCreateUserModal.set(true);
    this.errorMessage.set('');
  }

  closeCreateUserModal(): void {
    this.showCreateUserModal.set(false);
    this.isCreating.set(false);
    this.errorMessage.set('');
    this.createForm = { username: '', email: '', password: '', role: 'USER' };
  }

  private validateCreateForm(): boolean {
    if (!this.createForm.username.trim()) {
      this.errorMessage.set('Username is required');
      return false;
    }
    if (!this.createForm.email.trim()) {
      this.errorMessage.set('Email is required');
      return false;
    }
    if (!this.createForm.password.trim()) {
      this.errorMessage.set('Password is required');
      return false;
    }
    return true;
  }

  // (createUser is handled above)
}


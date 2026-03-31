import { Component, inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { ToastService } from '../../../services/toast.service';

@Component({
  selector: 'app-accept-invite',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './accept-invite.html',
  styleUrls: ['./accept-invite.css'],
})
export class AcceptInviteComponent {
  private readonly apiService = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly platformId = inject(PLATFORM_ID);

  token: string | null = null;
  loading = false;

  form = {
    username: '',
    password: '',
  };

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (!this.token && isPlatformBrowser(this.platformId)) {
      // Token should come from query params; if not, show an error.
      this.toast.error('Missing invitation token.');
    }
  }

  accept(): void {
    if (!this.token) {
      this.toast.error('Missing invitation token.');
      return;
    }
    if (!this.form.username.trim()) {
      this.toast.error('Username is required.');
      return;
    }
    if (!this.form.password.trim()) {
      this.toast.error('Password is required.');
      return;
    }

    this.loading = true;
    this.apiService
      .acceptUserInvitation({
        token: this.token,
        username: this.form.username.trim(),
        password: this.form.password,
      })
      .subscribe({
        next: () => {
          this.toast.success('Invitation accepted. You can now log in.');
          this.loading = false;
          this.router.navigate(['/auth']);
        },
        error: (err) => {
          const msg = err?.message || err?.error?.message || 'Failed to accept invitation.';
          this.toast.error(msg);
          this.loading = false;
        },
      });
  }
}


import { Component, PLATFORM_ID, Inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { ToastContainerComponent } from '../../components/toast-container/toast-container';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [CommonModule, FormsModule, ToastContainerComponent],
  templateUrl: './auth.html',
  styleUrls: ['./auth.css'],
})
export class AuthComponent {
  // Only login is supported; sign-up is invite-based and handled elsewhere.
  isLogin = true;
  loading = false;
  errorMessage = '';
  successMessage = '';
  showLoginPassword = false;
  private returnUrl = '/admin/dashboard';

  // Login fields
  loginUsername = '';
  loginPassword = '';

  constructor(
    private apiService: ApiService,
    private router: Router,
    private route: ActivatedRoute,
    private toast: ToastService,
    @Inject(PLATFORM_ID) private platformId: Object,
  ) {
    const requested = this.route.snapshot.queryParamMap.get('returnUrl');
    if (requested && requested.startsWith('/')) {
      this.returnUrl = requested;
    }
    this.checkIfAlreadyLoggedIn();
  }

  checkIfAlreadyLoggedIn() {
    if (isPlatformBrowser(this.platformId)) {
      const token = localStorage.getItem('authToken');
      if (token) {
        this.router.navigateByUrl(this.returnUrl);
      }
    }
  }

  login() {
    if (!this.loginUsername || !this.loginPassword) {
      this.errorMessage = 'Username/Email and password are required';
      this.toast.error(this.errorMessage);
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    this.apiService.login({ identifier: this.loginUsername, password: this.loginPassword }).subscribe({
      next: (response) => {
        if (response.data && response.data.id) {
          if (isPlatformBrowser(this.platformId)) {
            localStorage.setItem('authToken', response.data.id);
            localStorage.setItem('currentUser', JSON.stringify(response.data));
          }

          this.toast.success('Login successful!');
          this.successMessage = 'Login successful!';
          setTimeout(() => {
            this.router.navigateByUrl(this.returnUrl);
          }, 1000);
        }
        this.loading = false;
      },
      error: (err) => {
        const msg = err?.message || 'Invalid username/email or password';
        this.errorMessage = msg;
        this.toast.error(this.errorMessage);
        this.loading = false;
      },
    });
  }

  toggleLoginPasswordVisibility() {
    this.showLoginPassword = !this.showLoginPassword;
  }
}

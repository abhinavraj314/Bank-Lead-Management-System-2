import { Component, PLATFORM_ID, Inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './auth.html',
  styleUrls: ['./auth.css'],
})
export class AuthComponent {
  isLogin = true;
  loading = false;
  errorMessage = '';
  successMessage = '';
  showLoginPassword = false;
  showSignupPassword = false;

  // Login fields
  loginUsername = '';
  loginPassword = '';

  // Signup fields
  signupUsername = '';
  signupEmail = '';
  signupPassword = '';
  signupRole: 'USER' | 'ADMIN' = 'USER';

  constructor(
    private apiService: ApiService,
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object,
  ) {
    this.checkIfAlreadyLoggedIn();
  }

  checkIfAlreadyLoggedIn() {
    if (isPlatformBrowser(this.platformId)) {
      const token = localStorage.getItem('authToken');
      if (token) {
        this.router.navigate(['/admin/dashboard']);
      }
    }
  }

  toggleMode() {
    this.isLogin = !this.isLogin;
    this.errorMessage = '';
    this.successMessage = '';
  }

  login() {
    if (!this.loginUsername || !this.loginPassword) {
      this.errorMessage = 'Username/Email and password are required';
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    // Try to find user by username first, then by email
    this.apiService.getUserByUsername(this.loginUsername).subscribe({
      next: (response) => {
        if (response.data && response.data.id) {
          if (isPlatformBrowser(this.platformId)) {
            localStorage.setItem('authToken', response.data.id);
            localStorage.setItem('currentUser', JSON.stringify(response.data));
          }

          this.successMessage = 'Login successful!';
          setTimeout(() => {
            this.router.navigate(['/admin/dashboard']);
          }, 1000);
        }
        this.loading = false;
      },
      error: (err) => {
        // If username not found, try email
        if (err.status === 404) {
          this.tryLoginByEmail();
        } else {
          this.errorMessage = 'Invalid username/email or password';
          this.loading = false;
        }
      },
    });
  }

  tryLoginByEmail() {
    this.apiService.getUserByEmail(this.loginUsername).subscribe({
      next: (response) => {
        if (response.data && response.data.id) {
          if (isPlatformBrowser(this.platformId)) {
            localStorage.setItem('authToken', response.data.id);
            localStorage.setItem('currentUser', JSON.stringify(response.data));
          }

          this.successMessage = 'Login successful!';
          setTimeout(() => {
            this.router.navigate(['/admin/dashboard']);
          }, 1000);
        }
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = 'Invalid username/email or password';
        this.loading = false;
      },
    });
  }

  signup() {
    if (!this.signupUsername || !this.signupEmail || !this.signupPassword) {
      this.errorMessage = 'All fields are required';
      return;
    }

    if (!this.isValidEmail(this.signupEmail)) {
      this.errorMessage = 'Invalid email format';
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    const createUserRequest = {
      username: this.signupUsername,
      email: this.signupEmail,
      password: this.signupPassword,
      role: this.signupRole,
    };

    this.apiService.createUser(createUserRequest).subscribe({
      next: (response) => {
        if (response.data) {
          this.successMessage = 'Account created successfully! Logging in...';
          if (isPlatformBrowser(this.platformId)) {
            localStorage.setItem('authToken', response.data.id);
            localStorage.setItem('currentUser', JSON.stringify(response.data));
          }

          setTimeout(() => {
            this.router.navigate(['/admin/dashboard']);
          }, 1500);
        }
        this.loading = false;
      },
      error: (err) => {
        if (err.error?.message) {
          this.errorMessage = err.error.message;
        } else {
          this.errorMessage = 'Error creating account. Please try again.';
        }
        this.loading = false;
      },
    });
  }

  private isValidEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }

  toggleLoginPasswordVisibility() {
    this.showLoginPassword = !this.showLoginPassword;
  }

  toggleSignupPasswordVisibility() {
    this.showSignupPassword = !this.showSignupPassword;
  }
}

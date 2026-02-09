import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router, CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root',
})
export class AuthGuard implements CanActivate {
  constructor(
    private apiService: ApiService,
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object,
  ) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    // During SSR, allow navigation to proceed (guard will work on browser)
    if (!isPlatformBrowser(this.platformId)) {
      return true;
    }

    if (this.apiService.isLoggedIn()) {
      // Check for admin-only routes
      if (route.data['adminOnly'] && !this.apiService.isAdmin()) {
        this.router.navigate(['/admin/dashboard']);
        return false;
      }
      return true;
    }

    // Not logged in, redirect to auth
    this.router.navigate(['/auth'], { queryParams: { returnUrl: state.url } });
    return false;
  }
}

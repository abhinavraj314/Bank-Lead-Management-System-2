import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  private readonly baseUrl = environment.apiUrl;

  constructor(
    private http: HttpClient,
    @Inject(PLATFORM_ID) private platformId: Object,
  ) {}

  /**
   * Generic GET request
   */
  get<T>(endpoint: string): Observable<T> {
    return this.http.get<T>(`${this.baseUrl}${endpoint}`).pipe(catchError(this.handleError));
  }

  /**
   * Generic POST request
   */
  post<T>(endpoint: string, body: any): Observable<T> {
    return this.http.post<T>(`${this.baseUrl}${endpoint}`, body).pipe(catchError(this.handleError));
  }

  /**
   * Generic PUT request
   */
  put<T>(endpoint: string, body: any): Observable<T> {
    return this.http.put<T>(`${this.baseUrl}${endpoint}`, body).pipe(catchError(this.handleError));
  }

  /**
   * Generic PATCH request
   */
  patch<T>(endpoint: string, body: any): Observable<T> {
    return this.http
      .patch<T>(`${this.baseUrl}${endpoint}`, body)
      .pipe(catchError(this.handleError));
  }

  /**
   * Generic DELETE request
   */
  delete<T>(endpoint: string): Observable<T> {
    return this.http.delete<T>(`${this.baseUrl}${endpoint}`).pipe(catchError(this.handleError));
  }

  /**
   * File upload with FormData
   */
  uploadFile<T>(endpoint: string, formData: FormData): Observable<T> {
    return this.http
      .post<T>(`${this.baseUrl}${endpoint}`, formData)
      .pipe(catchError(this.handleError));
  }

  // ==================== USER ENDPOINTS ====================

  /**
   * Create a new user (Sign up)
   */
  createUser(request: any): Observable<any> {
    return this.post('/users', request);
  }

  /**
   * Get all users with pagination
   */
  getUsers(
    page: number = 1,
    limit: number = 20,
    sortBy: string = 'createdAt',
    order: string = 'desc',
  ): Observable<any> {
    return this.get(`/users?page=${page}&limit=${limit}&sortBy=${sortBy}&order=${order}`);
  }

  /**
   * Get user by ID
   */
  getUserById(id: string): Observable<any> {
    return this.get(`/users/${id}`);
  }

  /**
   * Get user by username (for login)
   */
  getUserByUsername(username: string): Observable<any> {
    return this.get(`/users/username/${username}`);
  }

  /**
   * Get user by email (for login)
   */
  getUserByEmail(email: string): Observable<any> {
    return this.get(`/users/email/${email}`);
  }

  /**
   * Update user
   */
  updateUser(id: string, updates: any): Observable<any> {
    return this.put(`/users/${id}`, updates);
  }

  /**
   * Delete user
   */
  deleteUser(id: string): Observable<any> {
    return this.delete(`/users/${id}`);
  }

  // ==================== AUTHENTICATION HELPERS ====================

  /**
   * Check if user is logged in
   */
  isLoggedIn(): boolean {
    if (!isPlatformBrowser(this.platformId)) {
      return false;
    }
    return !!localStorage.getItem('authToken');
  }

  /**
   * Get current user from localStorage
   */
  getCurrentUser(): any {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }
    const userStr = localStorage.getItem('currentUser');
    return userStr ? JSON.parse(userStr) : null;
  }

  /**
   * Logout user
   */
  logout(): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.removeItem('authToken');
      localStorage.removeItem('currentUser');
    }
  }

  /**
   * Get auth token
   */
  getAuthToken(): string | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }
    return localStorage.getItem('authToken');
  }

  /**
   * Get current user role
   */
  getUserRole(): string | null {
    const user = this.getCurrentUser();
    return user?.role || null;
  }

  /**
   * Check if user is admin
   */
  isAdmin(): boolean {
    return this.getUserRole() === 'ADMIN';
  }

  private handleError = (error: HttpErrorResponse): Observable<never> => {
    let errorMessage = 'An unknown error occurred';

    // SSR-safe check: ErrorEvent only exists in browser, not in Node.js
    const isErrorEvent = typeof ErrorEvent !== 'undefined' && error.error instanceof ErrorEvent;

    if (isErrorEvent) {
      // Client-side error (browser only)
      errorMessage = `Error: ${error.error.message}`;
      console.error('Client-side error:', error.error);
    } else {
      // Server-side error or SSR context
      const status = error.status;
      const message = error.error?.error?.message || error.error?.message || error.message;

      switch (status) {
        case 400:
          errorMessage = `Bad Request: ${message}`;
          break;
        case 401:
          errorMessage = `Unauthorized: ${message}`;
          break;
        case 403:
          errorMessage = `Forbidden: ${message}`;
          break;
        case 404:
          errorMessage = `Not Found: ${message}`;
          break;
        case 409:
          errorMessage = `Conflict: ${message}`;
          break;
        case 500:
          errorMessage = `Server Error: ${message}`;
          break;
        default:
          errorMessage = `Error ${status}: ${message}`;
      }

      console.error(`Server-side error (${status}):`, error.error);
    }

    // Return user-friendly error
    return throwError(() => new Error(errorMessage));
  };
}

import { Component, OnInit, signal, inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../../services/product.service';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { ProductWithStatus, TeamDto, ApiResponse } from '../../models/lead.models';

@Component({
  selector: 'app-products',
  imports: [CommonModule, FormsModule],
  templateUrl: './products.html',
  styleUrl: './products.css',
})
export class ProductsPage implements OnInit {
  private readonly productService = inject(ProductService);
  private readonly apiService = inject(ApiService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly toast = inject(ToastService);

  protected readonly products = signal<ProductWithStatus[]>([]);
  protected readonly showCreateModal = signal<boolean>(false);
  protected readonly isCreating = signal<boolean>(false);
  protected readonly isDeleting = signal<boolean>(false);
  protected readonly errorMessage = signal<string>('');
  protected readonly teams = signal<TeamDto[]>([]);
  protected newProductName = '';
  protected newProductTeamId = '';

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.loadTeams();
      this.loadProducts();
    }
  }

  isAdmin(): boolean {
    return this.apiService.isAdmin();
  }

  /**
   * Load all teams for team selection dropdown
   */
  loadTeams(): void {
    this.apiService.get<ApiResponse<TeamDto[]>>('/teams').subscribe({
      next: (r) => {
        this.teams.set(r.data ?? []);
      },
      error: () => {
        this.toast.error('Failed to load teams');
      },
    });
  }

  loadProducts(): void {
    this.productService.getProducts().subscribe({
      next: (products) => {
        const productsWithStatus: ProductWithStatus[] = products.map((p, index) => ({
          ...p,
          status: index % 3 === 0 ? 'inactive' : 'active',
        }));
        this.products.set(productsWithStatus);
      },
      error: (error) => {
        const msg = error?.message || 'Failed to load products';
        this.toast.error(msg);
      },
    });
  }

  openCreateModal(): void {
    this.showCreateModal.set(true);
    this.errorMessage.set('');
    this.newProductName = '';
    this.newProductTeamId = '';
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
    this.newProductName = '';
    this.newProductTeamId = '';
    this.errorMessage.set('');
    this.isCreating.set(false);
  }

  onCreateProduct(): void {
    // Validation
    if (!this.newProductName.trim()) {
      this.errorMessage.set('Product Name is required');
      this.toast.error('Product Name is required');
      return;
    }

    if (!this.newProductTeamId) {
      this.errorMessage.set('Team is required');
      this.toast.error('Team is required');
      return;
    }

    this.isCreating.set(true);
    this.errorMessage.set('');

    // Call backend with teamId
    this.apiService
      .post<ApiResponse<any>>('/products', {
        p_name: this.newProductName.trim(),
        team_id: this.newProductTeamId,
      })
      .subscribe({
        next: () => {
          this.loadProducts(); // Reload from backend
          this.closeCreateModal();
          this.toast.success('Product created successfully');
        },
        error: (error) => {
          this.isCreating.set(false);
          const msg = error.error?.message || error.message || 'Failed to create product';
          this.errorMessage.set(msg);
          this.toast.error(msg);
        },
      });
  }

  onDeleteProduct(productId: string): void {
    if (!confirm('Are you sure you want to delete this product? This action cannot be undone.')) {
      return;
    }

    this.isDeleting.set(true);
    this.productService.deleteProduct(productId).subscribe({
      next: () => {
        this.loadProducts();
        this.isDeleting.set(false);
      },
      error: (error) => {
        this.isDeleting.set(false);
        this.toast.error('Failed to delete product: ' + (error.message || 'Unknown error'));
      },
    });
  }
}

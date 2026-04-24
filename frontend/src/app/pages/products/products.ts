import { Component, OnInit, computed, signal, inject, PLATFORM_ID } from '@angular/core';
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
  protected readonly isLoading = signal<boolean>(false);
  protected readonly errorMessage = signal<string>('');
  protected readonly teams = signal<TeamDto[]>([]);
  protected readonly currentPage = signal<number>(1);
  protected readonly pageSize = signal<number>(10);
  protected readonly totalProducts = signal<number>(0);
  protected readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.totalProducts() / this.pageSize())),
  );
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
    this.isLoading.set(true);
    this.productService
      .getProductsPage({ page: this.currentPage(), limit: this.pageSize() })
      .subscribe({
        next: ({ items, total }) => {
          const productsWithStatus: ProductWithStatus[] = items.map((p, index) => ({
            ...p,
            status: index % 3 === 0 ? 'inactive' : 'active',
          }));
          this.products.set(productsWithStatus);
          this.totalProducts.set(total);
          this.isLoading.set(false);
        },
        error: (error) => {
          const msg = error?.message || 'Failed to load products';
          this.toast.error(msg);
          this.products.set([]);
          this.totalProducts.set(0);
          this.isLoading.set(false);
        },
      });
  }

  protected goToPage(page: number): void {
    if (page < 1 || page > this.totalPages() || page === this.currentPage()) {
      return;
    }
    this.currentPage.set(page);
    this.loadProducts();
  }

  protected prevPage(): void {
    this.goToPage(this.currentPage() - 1);
  }

  protected nextPage(): void {
    this.goToPage(this.currentPage() + 1);
  }

  protected onPageSizeChange(size: number): void {
    const parsed = Number(size) || 10;
    this.pageSize.set(parsed);
    this.currentPage.set(1);
    this.loadProducts();
  }

  protected paginationText(): string {
    if (this.totalProducts() === 0) {
      return 'Showing 0 of 0';
    }
    const start = (this.currentPage() - 1) * this.pageSize() + 1;
    const end = Math.min(this.currentPage() * this.pageSize(), this.totalProducts());
    return `Showing ${start}-${end} of ${this.totalProducts()}`;
  }

  private reloadFirstPageIfNeeded(): void {
    const hasOnlyOneItemOnCurrentPage =
      this.products().length === 1 && this.currentPage() > 1 && this.totalProducts() > 1;
    if (hasOnlyOneItemOnCurrentPage) {
      this.currentPage.set(this.currentPage() - 1);
    } else if (this.currentPage() < 1) {
      this.currentPage.set(1);
    }
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
          this.currentPage.set(1);
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
        this.reloadFirstPageIfNeeded();
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

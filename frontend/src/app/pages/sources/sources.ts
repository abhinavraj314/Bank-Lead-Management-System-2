import { Component, OnInit, computed, signal, inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SourceService } from '../../services/source.service';
import { ProductService } from '../../services/product.service';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { Source } from '../../models/lead.models';

@Component({
  selector: 'app-sources',
  imports: [CommonModule, FormsModule],
  templateUrl: './sources.html',
  styleUrl: './sources.css',
})
export class SourcesPage implements OnInit {
  private readonly sourceService = inject(SourceService);
  private readonly productService = inject(ProductService);
  private readonly apiService = inject(ApiService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly toast = inject(ToastService);

  protected readonly sources = signal<Source[]>([]);
  protected readonly products = signal<{ product_id: string; product_name: string }[]>([]);
  protected readonly showCreateModal = signal<boolean>(false);
  protected readonly isCreating = signal<boolean>(false);
  protected readonly isDeleting = signal<boolean>(false);
  protected readonly isLoading = signal<boolean>(false);
  protected readonly errorMessage = signal<string>('');
  protected readonly currentPage = signal<number>(1);
  protected readonly pageSize = signal<number>(10);
  protected readonly totalSources = signal<number>(0);
  protected readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.totalSources() / this.pageSize())),
  );
  protected readonly currentStep = signal<number>(1); // 1: Source details, 2: Columns
  protected readonly showColumnsModal = signal<boolean>(false);
  protected readonly selectedSource = signal<Source | null>(null);
  protected newSource: { source_name: string; product_id: string; columns: string[] } = {
    source_name: '',
    product_id: '',
    columns: [''], // Start with one empty column
  };

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.loadSources();
      this.loadProducts();
    }
  }

  isAdmin(): boolean {
    return this.apiService.isAdmin();
  }

  loadSources(): void {
    this.isLoading.set(true);
    this.sourceService
      .getSourcesPage({ page: this.currentPage(), limit: this.pageSize() })
      .subscribe({
        next: ({ items, total }) => {
          const sourcesWithStatus: Source[] = items.map((s) => ({
            ...s,
            status: s.status || 'active',
          }));
          this.sources.set(sourcesWithStatus);
          this.totalSources.set(total);
          this.isLoading.set(false);
        },
        error: (error) => {
          const msg = error?.message || 'Failed to load sources';
          this.toast.error(msg);
          this.sources.set([]);
          this.totalSources.set(0);
          this.isLoading.set(false);
        },
      });
  }

  loadProducts(): void {
    this.productService.getProducts().subscribe({
      next: (products) => {
        this.products.set(products);
      },
      error: (error) => {
        const msg = error?.message || 'Failed to load products';
        this.toast.error(msg);
      },
    });
  }

  getProductName(productId: string): string {
    const product = this.products().find((p) => p.product_id === productId);
    return product?.product_name || '-';
  }

  getColumnsDisplay(columns: string[] | undefined): string {
    if (!columns || columns.length === 0) {
      return '-';
    }
    return columns.join(', ');
  }

  openCreateModal(): void {
    this.showCreateModal.set(true);
    this.errorMessage.set('');
    this.currentStep.set(1);
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
    this.errorMessage.set('');
    this.isCreating.set(false);
    this.currentStep.set(1);
    this.newSource = { source_name: '', product_id: '', columns: [''] };
  }

  nextStep(): void {
    if (this.currentStep() === 1) {
      // Validate step 1
      if (!this.newSource.source_name.trim()) {
        this.errorMessage.set('Source Name is required');
        this.toast.error('Source Name is required');
        return;
      }
      if (!this.newSource.product_id) {
        this.errorMessage.set('Product is required');
        this.toast.error('Product is required');
        return;
      }
      this.errorMessage.set('');
      this.currentStep.set(2);
    }
  }

  prevStep(): void {
    this.currentStep.set(1);
    this.errorMessage.set('');
  }

  addColumn(): void {
    this.newSource.columns.push('');
  }

  removeColumn(index: number): void {
    this.newSource.columns.splice(index, 1);
  }

  onCreateSource(): void {
    // Validate columns
    const validColumns = this.newSource.columns.filter((c) => c.trim() !== '');
    if (validColumns.length === 0) {
      this.errorMessage.set('At least one column is required');
      this.toast.error('At least one column is required');
      return;
    }

    this.isCreating.set(true);
    this.errorMessage.set('');

    this.sourceService
      .createSource({
        s_name: this.newSource.source_name.trim(),
        p_id: this.newSource.product_id,
        columns: validColumns,
      })
      .subscribe({
        next: () => {
          this.currentPage.set(1);
          this.loadSources(); // Reload from backend
          this.closeCreateModal();
        },
        error: (error) => {
          this.isCreating.set(false);
          const msg = error.message || 'Failed to create source';
          this.errorMessage.set(msg);
          this.toast.error(msg);
        },
      });
  }

  getColumnsPreview(columns: string[] | undefined): string {
    if (!columns || columns.length === 0) {
      return '-';
    }
    // Show first 3 columns
    return columns.slice(0, 3).join(', ') + (columns.length > 3 ? '...' : '');
  }

  openColumnsModal(source: Source): void {
    this.selectedSource.set(source);
    this.showColumnsModal.set(true);
  }

  closeColumnsModal(): void {
    this.showColumnsModal.set(false);
    this.selectedSource.set(null);
  }

  onDeleteSource(sourceId: string): void {
    if (!confirm('Are you sure you want to delete this source? This action cannot be undone.')) {
      return;
    }

    this.isDeleting.set(true);
    this.sourceService.deleteSource(sourceId).subscribe({
      next: () => {
        const hasOnlyOneItemOnCurrentPage =
          this.sources().length === 1 && this.currentPage() > 1 && this.totalSources() > 1;
        if (hasOnlyOneItemOnCurrentPage) {
          this.currentPage.set(this.currentPage() - 1);
        }
        this.loadSources();
        this.isDeleting.set(false);
      },
      error: (error) => {
        this.isDeleting.set(false);
        this.toast.error('Failed to delete source: ' + (error.message || 'Unknown error'));
      },
    });
  }

  protected goToPage(page: number): void {
    if (page < 1 || page > this.totalPages() || page === this.currentPage()) {
      return;
    }
    this.currentPage.set(page);
    this.loadSources();
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
    this.loadSources();
  }

  protected paginationText(): string {
    if (this.totalSources() === 0) {
      return 'Showing 0 of 0';
    }
    const start = (this.currentPage() - 1) * this.pageSize() + 1;
    const end = Math.min(this.currentPage() * this.pageSize(), this.totalSources());
    return `Showing ${start}-${end} of ${this.totalSources()}`;
  }
}

import { Component, OnInit, signal, inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../../services/product.service';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { ProductWithStatus } from '../../models/lead.models';

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
  protected newProductId = '';
  protected newProductName = '';

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.loadProducts();
    }
  }

  isAdmin(): boolean {
    return this.apiService.isAdmin();
  }

  loadProducts(): void {
    this.productService.getProducts().subscribe((products) => {
      // Add mock status and created_date for display
      const productsWithStatus: ProductWithStatus[] = products.map((p, index) => ({
        ...p,
        status: index % 3 === 0 ? 'inactive' : 'active',
        created_date: new Date(2024, 0, 15 - index).toISOString().split('T')[0],
      }));
      this.products.set(productsWithStatus);
    });
  }

  openCreateModal(): void {
    this.showCreateModal.set(true);
    this.errorMessage.set('');
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
    this.newProductName = '';
    this.newProductId = '';
    this.errorMessage.set('');
    this.isCreating.set(false);
  }

  onCreateProduct(): void {
    // Validation
    if (!this.newProductId.trim()) {
      this.errorMessage.set('Product ID is required');
      return;
    }
    if (!this.newProductName.trim()) {
      this.errorMessage.set('Product Name is required');
      return;
    }

    this.isCreating.set(true);
    this.errorMessage.set('');

    this.productService
      .createProduct(this.newProductId.trim(), this.newProductName.trim())
      .subscribe({
        next: () => {
          this.loadProducts(); // Reload from backend
          this.closeCreateModal();
        },
        error: (error) => {
          this.isCreating.set(false);
          this.errorMessage.set(error.message || 'Failed to create product');
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

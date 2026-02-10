import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CanonicalFieldService } from '../../services/canonical-field.service';
import { DeduplicationRulesService } from '../../services/deduplication-rules.service';
import { ProductService } from '../../services/product.service';
import { ApiService } from '../../services/api.service';
import { CanonicalField, Product } from '../../models/lead.models';

@Component({
  selector: 'app-deduplication-rules',
  imports: [CommonModule, FormsModule],
  templateUrl: './deduplication-rules.html',
  styleUrl: './deduplication-rules.css',
})
export class DeduplicationRulesPage implements OnInit {
  private readonly canonicalFieldService = inject(CanonicalFieldService);
  private readonly deduplicationRulesService = inject(DeduplicationRulesService);
  private readonly productService = inject(ProductService);
  private readonly apiService = inject(ApiService);

  protected readonly products = signal<Product[]>([]);
  protected readonly fields = signal<CanonicalField[]>([]);
  protected readonly selectedProduct = signal<string>('');
  protected readonly selectedFields = signal<string[]>([]);
  protected readonly isSaving = signal<boolean>(false);
  protected readonly errorMessage = signal<string>('');
  protected readonly successMessage = signal<string>('');

  ngOnInit(): void {
    this.loadProducts();
    this.loadFields();
  }

  isAdmin(): boolean {
    return this.apiService.isAdmin();
  }

  loadProducts(): void {
    this.productService.getProducts().subscribe((products) => {
      this.products.set(products);
    });
  }

  loadFields(): void {
    this.canonicalFieldService.getCanonicalFields().subscribe((fields) => {
      this.fields.set(fields);
    });
  }

  /**
   * Called when user changes product selection
   * Resets selected fields and loads rules for the new product
   */
  onProductChanged(productId: string): void {
    this.selectedProduct.set(productId);
    this.errorMessage.set('');

    if (!productId) {
      // Product cleared
      this.selectedFields.set([]);
      return;
    }

    // Load existing rules for this product
    this.deduplicationRulesService.getRulesForProduct(productId).subscribe((rules) => {
      this.selectedFields.set(rules);
    });
  }

  /**
   * Toggle a canonical field for deduplication
   */
  toggleField(fieldId: string | undefined): void {
    if (!fieldId) return;

    const current = this.selectedFields();
    if (current.includes(fieldId)) {
      this.selectedFields.set(current.filter((id) => id !== fieldId));
    } else {
      this.selectedFields.set([...current, fieldId]);
    }
  }

  isFieldSelected(fieldId: string | undefined): boolean {
    if (!fieldId) return false;
    return this.selectedFields().includes(fieldId);
  }

  /**
   * Validate and save deduplication rules for the selected product
   */
  onSaveRules(): void {
    this.errorMessage.set('');

    // Validation: Product must be selected
    if (!this.selectedProduct()) {
      this.errorMessage.set('Please select a product first');
      return;
    }

    // Validation: At least one canonical field must be selected
    if (this.selectedFields().length === 0) {
      this.errorMessage.set('Please select at least one canonical field');
      return;
    }

    this.isSaving.set(true);

    // Save rules for the selected product
    this.deduplicationRulesService
      .saveRulesForProduct(this.selectedProduct(), this.selectedFields())
      .subscribe({
        next: () => {
          this.isSaving.set(false);
          this.successMessage.set(
            `Deduplication rules saved successfully for product: ${this.getProductName(
              this.selectedProduct(),
            )}`,
          );
          setTimeout(() => this.successMessage.set(''), 4000);
        },
        error: (error) => {
          this.isSaving.set(false);
          this.errorMessage.set(error.message || 'Failed to save deduplication rules');
        },
      });
  }

  getProductName(productId: string): string {
    const product = this.products().find((p) => p.product_id === productId);
    return product?.product_name || productId;
  }
}

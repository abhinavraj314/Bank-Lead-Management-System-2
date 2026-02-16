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
  protected readonly isExecuting = signal<boolean>(false);
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
   * Loads rules from backend for the selected product (canonical field names: email, phone_number, aadhar_number)
   */
  onProductChanged(productId: string): void {
    this.selectedProduct.set(productId);
    this.errorMessage.set('');

    if (!productId) {
      this.selectedFields.set([]);
      return;
    }

    this.deduplicationRulesService.getRulesForProduct(productId).subscribe((rules) => {
      this.selectedFields.set(rules);
    });
  }

  /**
   * Toggle a canonical field for deduplication (uses field_name, not field_id)
   */
  toggleField(fieldName: string | undefined): void {
    if (!fieldName) return;

    const current = this.selectedFields();
    if (current.includes(fieldName)) {
      this.selectedFields.set(current.filter((n) => n !== fieldName));
    } else {
      this.selectedFields.set([...current, fieldName]);
    }
  }

  isFieldSelected(fieldName: string | undefined): boolean {
    if (!fieldName) return false;
    return this.selectedFields().includes(fieldName);
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
          this.loadProducts(); // Refresh so "Saved Rules for All Products" table updates
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

  /** Run deduplication for the selected product using its config */
  onExecuteForProduct(): void {
    const pId = this.selectedProduct();
    if (!pId) {
      this.errorMessage.set('Please select a product first');
      return;
    }
    this.errorMessage.set('');
    this.isExecuting.set(true);
    this.deduplicationRulesService.executeDeduplicationForProduct(pId).subscribe({
      next: (stats) => {
        this.isExecuting.set(false);
        this.successMessage.set(
          `Deduplication completed for ${this.getProductName(pId)}: ${stats.mergedCount} lead(s) merged`
        );
        setTimeout(() => this.successMessage.set(''), 5000);
      },
      error: (err) => {
        this.isExecuting.set(false);
        this.errorMessage.set(err?.message || 'Deduplication failed');
      },
    });
  }

  /** Run deduplication for all products (each uses its own config) */
  onExecuteForAllProducts(): void {
    this.errorMessage.set('');
    this.isExecuting.set(true);
    this.deduplicationRulesService.executeDeduplicationForAllProducts().subscribe({
      next: (results) => {
        this.isExecuting.set(false);
        const totalMerged = Object.values(results).reduce(
          (sum, s) => sum + (s?.mergedCount ?? 0),
          0
        );
        this.successMessage.set(
          `Deduplication completed for all products: ${totalMerged} lead(s) merged across ${Object.keys(results).length} product(s)`
        );
        setTimeout(() => this.successMessage.set(''), 5000);
      },
      error: (err) => {
        this.isExecuting.set(false);
        this.errorMessage.set(err?.message || 'Deduplication failed');
      },
    });
  }
}

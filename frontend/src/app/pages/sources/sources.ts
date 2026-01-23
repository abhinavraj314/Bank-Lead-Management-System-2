import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SourceService } from '../../services/source.service';
import { ProductService } from '../../services/product.service';
import { Source } from '../../models/lead.models';

@Component({
  selector: 'app-sources',
  imports: [CommonModule, FormsModule],
  templateUrl: './sources.html',
  styleUrl: './sources.css'
})
export class SourcesPage implements OnInit {
  private readonly sourceService = inject(SourceService);
  private readonly productService = inject(ProductService);
  
  protected readonly sources = signal<Source[]>([]);
  protected readonly products = signal<{ product_id: string; product_name: string }[]>([]);
  protected readonly showCreateModal = signal<boolean>(false);
  protected readonly isCreating = signal<boolean>(false);
  protected readonly errorMessage = signal<string>('');
  protected readonly currentStep = signal<number>(1); // 1: Source details, 2: Columns
  protected newSource: { source_name: string; product_id: string; columns: string[] } = {
    source_name: '',
    product_id: '',
    columns: [''] // Start with one empty column
  };

  ngOnInit(): void {
    this.loadSources();
    this.loadProducts();
  }

  loadSources(): void {
    this.sourceService.getSources().subscribe(sources => {
      // Add mock status
      const sourcesWithStatus: Source[] = sources.map(s => ({
        ...s,
        status: 'active'
      }));
      this.sources.set(sourcesWithStatus);
    });
  }

  loadProducts(): void {
    this.productService.getProducts().subscribe(products => {
      this.products.set(products);
    });
  }

  getProductName(productId: string): string {
    const product = this.products().find(p => p.product_id === productId);
    return product?.product_name || '-';
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
        return;
      }
      if (!this.newSource.product_id) {
        this.errorMessage.set('Product is required');
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
    const validColumns = this.newSource.columns.filter(c => c.trim() !== '');
    if (validColumns.length === 0) {
      this.errorMessage.set('At least one column is required');
      return;
    }

    this.isCreating.set(true);
    this.errorMessage.set('');

    // Generate s_id from source name
    const sId = this.newSource.source_name.toUpperCase().replace(/\s+/g, '_');

    this.sourceService.createSource({
      s_id: sId,
      s_name: this.newSource.source_name.trim(),
      p_id: this.newSource.product_id,
      columns: validColumns
    }).subscribe({
      next: () => {
        this.loadSources(); // Reload from backend
        this.closeCreateModal();
      },
      error: (error) => {
        this.isCreating.set(false);
        this.errorMessage.set(error.message || 'Failed to create source');
      }
    });
  }
}

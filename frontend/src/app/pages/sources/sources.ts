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
  protected newSource: { source_name: string; product_id: string } = {
    source_name: '',
    product_id: ''
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
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
    this.newSource = { source_name: '', product_id: '' };
  }

  onCreateSource(): void {
    if (!this.newSource.source_name.trim() || !this.newSource.product_id) return;
    
    // Mock: Add to local state
    const source: Source = {
      source_id: `src_${Date.now()}`,
      source_name: this.newSource.source_name,
      product_id: this.newSource.product_id,
      status: 'active'
    };
    
    this.sources.set([...this.sources(), source]);
    this.closeCreateModal();
  }
}

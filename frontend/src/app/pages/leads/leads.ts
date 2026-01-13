import { Component, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ProductService } from '../../services/product.service';
import { SourceService } from '../../services/source.service';
import { LeadService } from '../../services/lead.service';

import { Product, Source, Lead } from '../../models/lead.models';

@Component({
  selector: 'app-leads',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './leads.html',
  styleUrl: './leads.css'
})
export class Leads {
  private productService = inject(ProductService);
  private sourceService = inject(SourceService);
  private leadService = inject(LeadService);

  selectedFile = signal<File | null>(null);
  selectedFileName = signal<string | null>(null);

  products = signal<Product[]>([]);
  sources = signal<Source[]>([]);
  allLeads = signal<Lead[]>([]);

  selectedProduct = signal('');
  selectedSource = signal('');
  searchQuery = signal('');
  selectedProductFilter = signal('');
  selectedSourceFilter = signal('');

  filteredLeads = computed(() =>
    this.allLeads().filter(lead => {
      const q = this.searchQuery().toLowerCase();
      return (
        (!q ||
          lead.name?.toLowerCase().includes(q) ||
          lead.email?.toLowerCase().includes(q) ||
          lead.phone?.includes(q)) &&
        (!this.selectedProductFilter() || lead.product_id === this.selectedProductFilter()) &&
        (!this.selectedSourceFilter() || lead.source_id === this.selectedSourceFilter())
      );
    })
  );

  constructor() {
    this.loadProducts();
    this.loadSources();
    this.loadLeads();
  }

  loadProducts() {
    this.productService.getProducts().subscribe(d => this.products.set(d));
  }

  loadSources() {
    this.sourceService.getSources().subscribe(d => this.sources.set(d));
  }

  loadLeads() {
    this.leadService.getLeads().subscribe(d => this.allLeads.set(d));
  }

  onFileSelected(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0] || null;
    this.selectedFile.set(file);
    this.selectedFileName.set(file?.name ?? null);
  }

  clearFile() {
    this.selectedFile.set(null);
    this.selectedFileName.set(null);
  }

  onUpload() {
    if (!this.selectedFile() || !this.selectedProduct() || !this.selectedSource()) return;

    this.leadService
      .uploadLeads(this.selectedFile()!, this.selectedProduct(), this.selectedSource())
      .subscribe(() => {
        this.clearFile();
        this.loadLeads();
      });
  }

  clearSearch() {
    this.searchQuery.set('');
  }

  clearFilters() {
    this.searchQuery.set('');
    this.selectedProductFilter.set('');
    this.selectedSourceFilter.set('');
  }
}

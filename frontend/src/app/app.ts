import { Component, signal, computed, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Lead, Product, Source } from './models/lead.models';
import { LeadService } from './services/lead.service';
import { ProductService } from './services/product.service';
import { SourceService } from './services/source.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  // Inject services
  private readonly leadService = inject(LeadService);
  private readonly productService = inject(ProductService);
  private readonly sourceService = inject(SourceService);

  protected readonly title = signal('Bank Lead Management System');
  protected readonly currentYear = computed(() => new Date().getFullYear());

  // Data signals
  protected readonly allLeads = signal<Lead[]>([]);
  protected readonly products = signal<Product[]>([]);
  protected readonly sources = signal<Source[]>([]);

  // Filter signals
  protected readonly searchQuery = signal<string>('');
  protected readonly selectedProductFilter = signal<string>('');
  protected readonly selectedSourceFilter = signal<string>('');

  // Upload form signals
  protected readonly selectedFileName = signal<string>('');
  protected readonly selectedProduct = signal<string>('');
  protected readonly selectedSource = signal<string>('');

  // Filtered leads based on search and filters
  protected readonly filteredLeads = computed(() => {
    let leads = this.allLeads();

    // Search filter
    const query = this.searchQuery().toLowerCase().trim();
    if (query) {
      leads = leads.filter(lead => {
        const nameMatch = lead.name?.toLowerCase().includes(query) ?? false;
        const emailMatch = lead.email?.toLowerCase().includes(query) ?? false;
        const phoneMatch = lead.phone?.toLowerCase().includes(query) ?? false;
        return nameMatch || emailMatch || phoneMatch;
      });
    }

    // Product filter (using product_id)
    const productFilter = this.selectedProductFilter();
    if (productFilter) {
      leads = leads.filter(lead => lead.product_id === productFilter);
    }

    // Source filter (using source_id)
    const sourceFilter = this.selectedSourceFilter();
    if (sourceFilter) {
      leads = leads.filter(lead => lead.source_id === sourceFilter);
    }

    return leads;
  });

  ngOnInit(): void {
    // Load data from mock services
    this.leadService.getLeads().subscribe(leads => {
      this.allLeads.set(leads);
    });

    this.productService.getProducts().subscribe(products => {
      this.products.set(products);
    });

    this.sourceService.getSources().subscribe(sources => {
      this.sources.set(sources);
    });
  }

  // File selection handler (UI only - no processing)
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      this.selectedFileName.set(file.name);
      // No file processing - UI only
    }
  }

  // Clear selected file
  clearFile(): void {
    this.selectedFileName.set('');
    const input = document.getElementById('fileInput') as HTMLInputElement;
    if (input) {
      input.value = '';
    }
  }

  // Upload button handler (no action - placeholder)
  onUpload(): void {
    // No upload logic - placeholder for future implementation
    console.log('Upload clicked - No action (frontend only)');
  }

  // Clear search
  clearSearch(): void {
    this.searchQuery.set('');
  }

  // Clear filters
  clearFilters(): void {
    this.selectedProductFilter.set('');
    this.selectedSourceFilter.set('');
    this.searchQuery.set('');
  }
}

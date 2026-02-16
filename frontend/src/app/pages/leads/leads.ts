import { Component, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ProductService } from '../../services/product.service';
import { SourceService } from '../../services/source.service';
import { LeadService } from '../../services/lead.service';
import { ApiService } from '../../services/api.service';

import { Product, Source, Lead } from '../../models/lead.models';

@Component({
  selector: 'app-leads',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './leads.html',
  styleUrl: './leads.css',
})
export class Leads {
  private productService = inject(ProductService);
  private sourceService = inject(SourceService);
  private leadService = inject(LeadService);
  private apiService = inject(ApiService);

  // Expose Math for template usage
  Math = Math;

  selectedFile = signal<File | null>(null);
  selectedFileName = signal<string | null>(null);

  isUploading = signal(false);
  uploadMessage = signal<string | null>(null);
  uploadError = signal<string | null>(null);
  validationErrors = signal<Array<{ rowNumber: number; reason: string }>>([]);
  uploadStats = signal<{
    insertedCount: number;
    mergedCount: number;
    failedCount: number;
    deduplication?: {
      totalLeadsBefore: number;
      duplicatesFound: number;
      mergedCount: number;
      finalLeadCount: number;
      error?: string;
    };
  } | null>(null);

  products = signal<Product[]>([]);
  sources = signal<Source[]>([]);
  allLeads = signal<Lead[]>([]);

  // Pagination
  currentPage = signal(1);
  pageSize = signal(10);
  totalLeads = signal(0);

  selectedProduct = signal('');
  selectedSource = signal('');
  searchQuery = signal('');
  selectedProductFilter = signal('');
  selectedSourceFilter = signal('');

  // Computed paginated leads
  paginatedLeads = computed(() => {
    const all = this.allLeads();
    const start = (this.currentPage() - 1) * this.pageSize();
    const end = start + this.pageSize();
    return all.slice(start, end);
  });

  filteredLeads = computed(() =>
    this.paginatedLeads().filter((lead) => {
      const q = this.searchQuery().toLowerCase();
      return (
        (!q ||
          lead.name?.toLowerCase().includes(q) ||
          lead.email?.toLowerCase().includes(q) ||
          lead.phone?.includes(q)) &&
        (!this.selectedProductFilter() || lead.product_id === this.selectedProductFilter()) &&
        (!this.selectedSourceFilter() || lead.source_id === this.selectedSourceFilter())
      );
    }),
  );

  totalPages = computed(() => Math.ceil(this.totalLeads() / this.pageSize()));

  constructor() {
    this.loadProducts();
    this.loadSources();
    this.loadLeads();
  }

  isAdmin(): boolean {
    return this.apiService.isAdmin();
  }

  loadProducts() {
    this.productService.getProducts().subscribe((d) => this.products.set(d));
  }

  loadSources() {
    this.sourceService.getSources().subscribe((d) => this.sources.set(d));
  }

  loadLeads() {
    this.leadService.getLeads({ limit: 10000 }).subscribe((result) => {
      this.allLeads.set(result.leads);
      this.totalLeads.set(result.total);
      this.currentPage.set(1);
    });
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

    this.isUploading.set(true);
    this.uploadMessage.set(null);
    this.uploadError.set(null);
    this.validationErrors.set([]);
    this.uploadStats.set(null);

    this.leadService
      .uploadLeads(this.selectedFile()!, this.selectedProduct(), this.selectedSource())
      .subscribe({
        next: (response) => {
          this.isUploading.set(false);
          this.uploadStats.set({
            insertedCount: response.insertedCount,
            mergedCount: response.mergedCount,
            failedCount: response.failedCount,
            deduplication: response.deduplication,
          });

          if (response.failedCount > 0 && response.failedRows) {
            this.validationErrors.set(
              response.failedRows.map((row) => ({
                rowNumber: row.rowNumber,
                reason: row.reason,
              })),
            );
            this.uploadMessage.set(
              `Upload completed with ${response.insertedCount} inserted, ${response.mergedCount} merged, and ${response.failedCount} failed rows`,
            );
          } else {
            let successMsg = '';

            // Build message based on what happened during upsert stage
            if (response.insertedCount > 0 && response.mergedCount > 0) {
              successMsg = `✅ Upload successful! ${response.insertedCount} new leads added, ${response.mergedCount} merged with existing leads (based on canonical field configuration)`;
            } else if (response.insertedCount > 0) {
              successMsg = `✅ Upload successful! ${response.insertedCount} new leads added`;
            } else if (response.mergedCount > 0) {
              successMsg = `✅ Upload successful! ${response.mergedCount} leads merged with existing leads (based on canonical field configuration)`;
            } else {
              successMsg = `✅ Upload successful!`;
            }

            // Add post-upload deduplication info
            if (response.deduplication) {
              if (response.deduplication.error) {
                successMsg += ` | ⚠️ Note: Post-upload deduplication encountered an error: ${response.deduplication.error}`;
              } else if (response.deduplication.duplicatesFound > 0) {
                // This means duplicates were found AFTER the upsert stage
                successMsg += ` | 🔄 Post-upload check: Found and merged ${response.deduplication.duplicatesFound} additional duplicate group(s) that existed in the database`;
              } else {
                // No duplicates in post-dedup stage is normal if some were already merged during upsert
                if (response.mergedCount === 0) {
                  // Only mention this if no merges happened during upsert
                  successMsg += ` | ✓ No duplicates found`;
                }
              }
            }

            this.uploadMessage.set(successMsg);
            this.clearFile();
            setTimeout(() => this.loadLeads(), 500);
          }
        },
        error: (error) => {
          this.isUploading.set(false);
          const errorResponse = error?.error;

          if (errorResponse?.error?.details) {
            // Backend returned detailed validation errors
            if (Array.isArray(errorResponse.error.details)) {
              this.validationErrors.set(
                errorResponse.error.details.map((detail: any) => ({
                  rowNumber: detail.rowNumber || detail.row || 'Header',
                  reason: detail.reason || detail.errors?.join('; ') || 'Validation error',
                })),
              );
            }
            this.uploadError.set(
              errorResponse.error.message || errorResponse.message || 'Upload validation failed',
            );
          } else {
            this.uploadError.set(errorResponse?.message || error?.message || 'Upload failed');
          }
        },
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

  clearMessages() {
    this.uploadMessage.set(null);
    this.uploadError.set(null);
    this.validationErrors.set([]);
    this.uploadStats.set(null);
  }

  // Pagination methods
  goToPage(page: number) {
    if (page >= 1 && page <= this.totalPages()) {
      this.currentPage.set(page);
    }
  }

  nextPage() {
    this.goToPage(this.currentPage() + 1);
  }

  previousPage() {
    this.goToPage(this.currentPage() - 1);
  }

  changePageSize(size: number) {
    this.pageSize.set(size);
    this.currentPage.set(1);
  }
}

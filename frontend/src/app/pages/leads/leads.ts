import { Component, signal, computed, inject, PLATFORM_ID, effect } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ProductService } from '../../services/product.service';
import { SourceService } from '../../services/source.service';
import { LeadService } from '../../services/lead.service';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';

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
  private toast = inject(ToastService);
  private platformId = inject(PLATFORM_ID);

  // Expose Math for template usage
  Math = Math;

  selectedFile = signal<File | null>(null);
  selectedFileName = signal<string | null>(null);

  isUploading = signal(false);
  uploadMessage = signal<string | null>(null);
  uploadError = signal<string | null>(null);
  loadError = signal<string | null>(null);
  loadingLeads = signal(false);
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

  // Pagination (server-side: one page fetched at a time)
  currentPage = signal(1);
  pageSize = signal(25);
  totalLeads = signal(0);
  jumpToPageInput = signal<number | null>(null);

  selectedProduct = signal('');
  selectedSource = signal('');
  searchQuery = signal('');
  selectedProductFilter = signal('');
  selectedSourceFilter = signal('');
  selectedStatusFilter = signal<string>('');
  selectedAssignedUserFilter = signal<string>('');

  // Users list for assignment dropdown (admin only)
  users = signal<Array<{ id: string; username: string; email: string }>>([]);
  loadingUsers = signal(false);

  // Inline editing state
  updatingLeadId = signal<string | null>(null);

  // Sort by score for ranking
  sortBy = signal<'created_at' | 'lead_score'>('created_at');

  // Server returns one page; sort client-side only for current page
  sortedLeads = computed(() => {
    const all = [...this.allLeads()];
    if (this.sortBy() === 'lead_score') {
      all.sort((a, b) => (b.lead_score ?? -1) - (a.lead_score ?? -1));
    }
    return all;
  });

  // Current page rows to show (server-side pagination: allLeads is already one page)
  paginatedLeads = computed(() => this.sortedLeads());

  totalPages = computed(() =>
    Math.max(1, Math.ceil(this.totalLeads() / this.pageSize())),
  );

  filteredTotal = computed(() => this.totalLeads());

  /** First 4 page numbers for compact pagination; rest via "Go to page" */
  visiblePageNumbers = computed(() => {
    const total = this.totalPages();
    const maxVisible = 4;
    return Array.from({ length: Math.min(maxVisible, total) }, (_, i) => i + 1);
  });

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      this.loadProducts();
      this.loadSources();
      if (this.isAdmin()) {
        this.loadUsers();
      }
      this.loadLeads();
    }
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

  loadUsers() {
    if (!this.isAdmin()) return;
    this.loadingUsers.set(true);
    this.apiService.getUsers(1, 1000).subscribe({
      next: (response: any) => {
        const content = response.data?.content || response.data || [];
        this.users.set(
          content.map((u: any) => ({
            id: u.id || u.userId || '',
            username: u.username || '',
            email: u.email || '',
          })),
        );
        this.loadingUsers.set(false);
      },
      error: () => {
        this.loadingUsers.set(false);
      },
    });
  }

  loadLeads() {
    this.loadError.set(null);
    this.loadingLeads.set(true);
    this.allLeads.set([]);
    const page = this.currentPage();
    const limit = this.pageSize();
    const p_id = this.selectedProductFilter() || undefined;
    const source_id = this.selectedSourceFilter() || undefined;
    const q = this.searchQuery().trim() || undefined;
    const status = this.selectedStatusFilter() || undefined;
    const assigned_user_id = this.selectedAssignedUserFilter() || undefined;
    const assigned_to_me = !this.isAdmin() ? true : undefined; // Sales users see only their leads by default
    this.leadService.getLeads({ page, limit, p_id, source_id, q, status, assigned_user_id, assigned_to_me }).subscribe({
      next: (result) => {
        this.allLeads.set(result.leads);
        this.totalLeads.set(result.total);
        this.loadingLeads.set(false);
      },
      error: (err) => {
        this.allLeads.set([]);
        this.totalLeads.set(0);
        this.loadError.set(err?.message || 'Failed to load leads');
        this.loadingLeads.set(false);
      },
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

            if (response.insertedCount > 0 && response.mergedCount > 0) {
              successMsg = `✅ Upload successful! ${response.insertedCount} new leads added, ${response.mergedCount} merged with existing leads (based on canonical field configuration)`;
            } else if (response.insertedCount > 0) {
              successMsg = `✅ Upload successful! ${response.insertedCount} new leads added`;
            } else if (response.mergedCount > 0) {
              successMsg = `✅ Upload successful! ${response.mergedCount} leads merged with existing leads (based on canonical field configuration)`;
            } else {
              successMsg = `✅ Upload successful!`;
            }

            if (response.deduplication) {
              if (response.deduplication.error) {
                successMsg += ` | ⚠️ Note: Post-upload deduplication encountered an error: ${response.deduplication.error}`;
              } else if (response.deduplication.duplicatesFound > 0) {
                successMsg += ` | 🔄 Post-upload check: Found and merged ${response.deduplication.duplicatesFound} additional duplicate group(s) that existed in the database`;
              } else {
                if (response.mergedCount === 0) {
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

  toggleSortByScore() {
    if (this.sortBy() === 'lead_score') {
      this.sortBy.set('created_at');
    } else {
      this.sortBy.set('lead_score');
    }
    this.currentPage.set(1);
  }

  getScoreClass(score: number | null | undefined): string {
    if (score == null) return 'score-none';
    if (score >= 0.7) return 'score-high';
    if (score >= 0.4) return 'score-medium';
    return 'score-low';
  }

  formatScore(score: number | null | undefined): string {
    if (score == null) return '—';
    return (score * 100).toFixed(1) + '%';
  }

  clearSearch() {
    this.searchQuery.set('');
    this.onFilterOrSearchChange();
  }

  clearFilters() {
    this.searchQuery.set('');
    this.selectedProductFilter.set('');
    this.selectedSourceFilter.set('');
    this.selectedStatusFilter.set('');
    this.selectedAssignedUserFilter.set('');
    this.currentPage.set(1);
    this.loadLeads();
  }

  // Status and assignment update methods
  updateLeadStatus(leadId: string, newStatus: 'NEW' | 'IN_PROGRESS' | 'QUALIFIED' | 'CLOSED') {
    if (this.updatingLeadId() === leadId) return; // Prevent double-click
    this.updatingLeadId.set(leadId);
    this.leadService.updateLeadStatus(leadId, newStatus).subscribe({
      next: (updatedLead) => {
        // Update the lead in the current list
        const leads = this.allLeads();
        const index = leads.findIndex((l) => l.lead_id === leadId);
        if (index >= 0) {
          leads[index] = updatedLead;
          this.allLeads.set([...leads]);
        }
        this.updatingLeadId.set(null);
        this.loadLeads();
      },
      error: (err) => {
        console.error('Failed to update status:', err);
        this.toast.error(err?.error?.message || err?.message || 'Failed to update status');
        this.updatingLeadId.set(null);
      },
    });
  }

  updateLeadAssignment(leadId: string, assignedUserId: string | null) {
    if (this.updatingLeadId() === leadId) return;
    this.updatingLeadId.set(leadId);
    this.leadService.updateLeadAssignment(leadId, assignedUserId).subscribe({
      next: (updatedLead) => {
        const leads = this.allLeads();
        const index = leads.findIndex((l) => l.lead_id === leadId);
        if (index >= 0) {
          leads[index] = updatedLead;
          this.allLeads.set([...leads]);
        }
        this.updatingLeadId.set(null);
        this.loadLeads();
      },
      error: (err) => {
        console.error('Failed to update assignment:', err);
        this.toast.error(err?.error?.message || err?.message || 'Failed to update assignment');
        this.updatingLeadId.set(null);
      },
    });
  }

  selfAssignLead(leadId: string) {
    if (this.updatingLeadId() === leadId) return;
    this.updatingLeadId.set(leadId);
    this.leadService.selfAssignLead(leadId).subscribe({
      next: (updatedLead) => {
        const leads = this.allLeads();
        const index = leads.findIndex((l) => l.lead_id === leadId);
        if (index >= 0) {
          leads[index] = updatedLead;
          this.allLeads.set([...leads]);
        }
        this.updatingLeadId.set(null);
        this.loadLeads(); // Refresh to show updated assignment
      },
      error: (err) => {
        console.error('Failed to assign lead:', err);
        this.toast.error(err?.error?.message || err?.message || 'Failed to assign lead');
        this.updatingLeadId.set(null);
      },
    });
  }

  getStatusDisplay(status: string | undefined): string {
    if (!status) return 'NEW';
    const s = status.toUpperCase();
    if (s === 'NEW') return 'New';
    if (s === 'IN_PROGRESS') return 'In Progress';
    if (s === 'QUALIFIED') return 'Qualified';
    if (s === 'CLOSED') return 'Closed';
    return status; // Fallback for old statuses
  }

  getStatusOptions(): Array<{ value: string; label: string }> {
    return [
      { value: 'NEW', label: 'New' },
      { value: 'IN_PROGRESS', label: 'In Progress' },
      { value: 'QUALIFIED', label: 'Qualified' },
      { value: 'CLOSED', label: 'Closed' },
    ];
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
      this.loadLeads();
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
    this.loadLeads();
  }

  /** Call when search or filter changes to refetch page 1 from server */
  onFilterOrSearchChange() {
    this.currentPage.set(1);
    this.loadLeads();
  }

  goToPageFromInput() {
    const val = this.jumpToPageInput();
    const total = this.totalPages();
    if (val != null && val >= 1 && val <= total) {
      this.goToPage(val);
      this.jumpToPageInput.set(null);
    }
  }
}

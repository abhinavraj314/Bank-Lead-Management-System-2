import { Component, signal, computed, inject, PLATFORM_ID, effect } from '@angular/core';
import { finalize } from 'rxjs';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ProductService } from '../../services/product.service';
import { SourceService } from '../../services/source.service';
import { LeadService } from '../../services/lead.service';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';

import { Product, Source, Lead, LeadHistoryData, LeadHistoryEvent } from '../../models/lead.models';

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

  // Sources dropdown for lead upload should only show sources
  // that belong to the currently selected product.
  sourcesForSelectedProduct = computed(() => {
    const pId = this.selectedProduct();
    if (!pId) return [];
    return this.sources().filter((s) => s.product_id === pId);
  });

  // Sources for modal lead creation (filtered by product selected in modal)
  sourcesForModalProduct = computed(() => {
    const pId = this.newLeadForm().pId;
    if (!pId) return [];
    return this.sources().filter((s) => s.product_id === pId);
  });

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
  showClosedLeads = signal(true);
  showFilterModal = signal(false);

  // Draft filter values used inside filter modal
  draftProductFilter = signal('');
  draftSourceFilter = signal('');
  draftStatusFilter = signal<string>('');
  draftAssignedUserFilter = signal<string>('');

  // Users list for assignment dropdown (admin only)
  users = signal<Array<{ id: string; username: string; email: string }>>([]);
  loadingUsers = signal(false);

  // Inline editing state
  updatingLeadId = signal<string | null>(null);

  // Individual lead creation modal state
  showCreateLeadModal = signal(false);
  creatingleadError = signal<string | null>(null);
  creatingLead = signal(false);
  newLeadForm = signal({
    name: '',
    email: '',
    phoneNumber: '',
    aadharNumber: '',
    pId: '',
    sourceId: '',
    income: null as number | null,
    creditScore: null as number | null,
    employmentType: null as 'SALARIED' | 'SELF_EMPLOYED' | null,
    loanAmount: null as number | null,
  });

  historyLead = signal<Lead | null>(null);
  historyLoading = signal(false);
  historyData = signal<LeadHistoryData | null>(null);
  historyError = signal<string | null>(null);

  /** Server applies sort; toggling refetches page 1 */
  sortBy = signal<'created_at' | 'lead_score'>('created_at');

  /** Current page rows from the API (server-side pagination) */
  paginatedLeads = computed(() => this.allLeads());

  totalPages = computed(() => Math.max(1, Math.ceil(this.totalLeads() / this.pageSize())));

  filteredTotal = computed(() => this.totalLeads());

  activeFilterCount = computed(() => {
    let count = 0;
    if (this.selectedProductFilter()) count++;
    if (this.selectedSourceFilter()) count++;
    if (this.selectedStatusFilter()) count++;
    if (this.selectedAssignedUserFilter()) count++;
    return count;
  });

  /** Sliding window of page numbers around the current page */
  visiblePageNumbers = computed(() => {
    const total = this.totalPages();
    const cur = this.currentPage();
    const maxVisible = 5;
    const half = Math.floor(maxVisible / 2);
    let end = Math.min(total, cur + half);
    let start = Math.max(1, end - maxVisible + 1);
    end = Math.min(total, start + maxVisible - 1);
    start = Math.max(1, end - maxVisible + 1);
    return Array.from({ length: end - start + 1 }, (_, i) => start + i);
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

  // Keep selectedSource valid when user switches the selected product.
  // If the current selectedSource doesn't belong to the product, clear it.
  constructorGuard = effect(() => {
    if (!isPlatformBrowser(this.platformId)) return;
    const pId = this.selectedProduct();
    const sId = this.selectedSource();

    if (!pId || !sId) return;

    const isValid = this.sources().some((s) => s.source_id === sId && s.product_id === pId);
    if (!isValid) this.selectedSource.set('');
  });

  // Keep modal source valid when user changes product in modal
  // If current sourceId doesn't belong to the selected product, clear it
  modalProductGuard = effect(() => {
    if (!isPlatformBrowser(this.platformId)) return;
    const form = this.newLeadForm();
    const pId = form.pId;
    const sId = form.sourceId;

    if (!pId || !sId) return;

    const isValid = this.sources().some((s) => s.source_id === sId && s.product_id === pId);
    if (!isValid) {
      this.updateNewLeadForm('sourceId', '');
    }
  });

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

  openCreateLeadModal() {
    this.newLeadForm.set({
      name: '',
      email: '',
      phoneNumber: '',
      aadharNumber: '',
      pId: '',
      sourceId: '',
      income: null,
      creditScore: null,
      employmentType: null,
      loanAmount: null,
    });
    this.creatingleadError.set(null);
    this.showCreateLeadModal.set(true);
  }

  closeCreateLeadModal() {
    this.showCreateLeadModal.set(false);
    this.creatingleadError.set(null);
  }

  submitCreateLead() {
    const form = this.newLeadForm();
    console.log('[DEBUG] Form values before validation:', {
      pId: form.pId,
      pIdTrimmed: form.pId?.trim(),
      pIdLength: form.pId?.length,
      sourceId: form.sourceId,
      sourceIdTrimmed: form.sourceId?.trim(),
      sourceIdLength: form.sourceId?.length,
      email: form.email,
      phoneNumber: form.phoneNumber,
      aadharNumber: form.aadharNumber,
    });

    const errors: string[] = [];

    // Validate product and source (trim whitespace for check)
    if (!form.pId || form.pId.trim() === '') {
      errors.push('Product is required');
    }
    if (!form.sourceId || form.sourceId.trim() === '') {
      errors.push('Source is required');
    }

    // Validate at least one identifier
    if (!form.email && !form.phoneNumber && !form.aadharNumber) {
      errors.push('At least one identifier (email, phone, or aadhar) is required');
    }

    // Validate email format if provided
    if (form.email && !form.email.includes('@')) {
      errors.push('Invalid email format');
    }

    // Validate phone number
    if (form.phoneNumber && !/^\d{10}$/.test(form.phoneNumber)) {
      errors.push('Phone number must be exactly 10 digits');
    }

    // Validate aadhar number
    if (form.aadharNumber && !/^\d{12}$/.test(form.aadharNumber)) {
      errors.push('Aadhar number must be exactly 12 digits');
    }

    // Validate credit score range
    if (form.creditScore !== null && (form.creditScore < 550 || form.creditScore > 850)) {
      errors.push('Credit score must be between 550 and 850');
    }

    // Validate income and loan amount
    if (form.income !== null && form.income < 0) {
      errors.push('Income must be non-negative');
    }
    if (form.loanAmount !== null && form.loanAmount < 0) {
      errors.push('Loan amount must be non-negative');
    }

    if (errors.length > 0) {
      this.creatingleadError.set(errors.join('; '));
      return;
    }

    this.creatingLead.set(true);
    this.creatingleadError.set(null);

    // Build request with trimmed values
    const requestBody: any = {
      pId: form.pId.trim(),
      sourceId: form.sourceId.trim(),
    };
    if (form.name && form.name.trim()) requestBody.name = form.name.trim();
    if (form.email && form.email.trim()) requestBody.email = form.email.trim();
    if (form.phoneNumber && form.phoneNumber.trim())
      requestBody.phoneNumber = form.phoneNumber.trim();
    if (form.aadharNumber && form.aadharNumber.trim())
      requestBody.aadharNumber = form.aadharNumber.trim();
    if (form.income !== null) requestBody.income = form.income;
    if (form.creditScore !== null) requestBody.creditScore = form.creditScore;
    if (form.employmentType) requestBody.employmentType = form.employmentType;
    if (form.loanAmount !== null) requestBody.loanAmount = form.loanAmount;

    console.log('[DEBUG] Final request body being sent to backend:', requestBody);
    console.log('[DEBUG] Request body JSON:', JSON.stringify(requestBody));
    console.log('[DEBUG] Request body keys:', Object.keys(requestBody));

    this.leadService.createLead(requestBody).subscribe({
      next: (lead) => {
        this.creatingLead.set(false);
        this.creatingleadError.set(null);
        this.toast.success(`Lead created successfully (ID: ${lead.lead_id})`);
        this.closeCreateLeadModal();
        // Refresh leads list
        this.currentPage.set(1);
        this.loadLeads();
      },
      error: (error) => {
        this.creatingLead.set(false);
        console.error('Lead creation error:', error);

        // Even on error, refresh leads in case it was partially created
        // (This handles race conditions or backend issues that create the lead despite returning error)
        setTimeout(() => {
          this.loadLeads();
        }, 500);

        // Try to extract meaningful error message
        let message = 'Failed to create lead';
        if (error?.error) {
          // If backend error has message field
          if (error.error.message) {
            message = error.error.message;
          } else if (error.error.error?.message) {
            message = error.error.error.message;
          } else if (error.error.details) {
            message = error.error.details;
          } else if (typeof error.error === 'string') {
            message = error.error;
          } else if (error.error.errors && Array.isArray(error.error.errors)) {
            // Spring validation errors array
            const validationErrors = error.error.errors as any[];
            if (validationErrors.length > 0) {
              message = validationErrors.map((e: any) => e.defaultMessage || e.message).join('; ');
            }
          }
        } else if (error?.message) {
          message = error.message;
        }

        // Only show error if we have a meaningful message
        if (message && message !== 'Failed to create lead') {
          this.creatingleadError.set(message);
        }
      },
    });
  }

  updateNewLeadForm(field: string, value: any) {
    const form = { ...this.newLeadForm() };
    (form as any)[field] = value;
    this.newLeadForm.set(form);
  }

  parseNumberInput(value: any): number | null {
    if (value === null || value === undefined || value === '') return null;
    const num = parseInt(value, 10);
    return isNaN(num) ? null : num;
  }

  loadLeads() {
    this.loadError.set(null);
    this.loadingLeads.set(true);
    this.allLeads.set([]);
    const p_id = this.selectedProductFilter() || undefined;
    const source_id = this.selectedSourceFilter() || undefined;
    const q = this.searchQuery().trim() || undefined;
    const status = this.selectedStatusFilter() || undefined;
    const assigned_user_id = this.selectedAssignedUserFilter() || undefined;
    const assigned_to_me = !this.isAdmin() ? true : undefined;
    const hide_terminal = !this.showClosedLeads();
    const sort = this.sortBy() === 'lead_score' ? 'leadScore' : 'createdAt';
    this.leadService
      .getLeads({
        page: this.currentPage(),
        limit: this.pageSize(),
        p_id,
        source_id,
        q,
        status,
        assigned_user_id,
        assigned_to_me,
        hide_terminal,
        sort,
        order: 'desc',
      })
      .pipe(finalize(() => this.loadingLeads.set(false)))
      .subscribe({
        next: (result) => {
          const total = Number(result.total) || 0;
          this.totalLeads.set(total);
          const maxPage = Math.max(1, Math.ceil(total / this.pageSize()) || 1);
          if (this.currentPage() > maxPage) {
            this.currentPage.set(maxPage);
            this.loadLeads();
            return;
          }
          this.allLeads.set(result.leads);
        },
        error: (err) => {
          this.allLeads.set([]);
          this.totalLeads.set(0);
          this.loadError.set(err?.message || 'Failed to load leads');
          this.toast.error(err?.message || 'Failed to load leads');
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
            this.toast.error(
              `Upload finished but ${response.failedCount} row(s) failed validation. Check the validation table below.`,
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
            this.toast.error(
              errorResponse.error.message || errorResponse.message || 'Upload validation failed',
            );
          } else {
            this.uploadError.set(errorResponse?.message || error?.message || 'Upload failed');
            this.toast.error(errorResponse?.message || error?.message || 'Upload failed');
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
    this.loadLeads();
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

  openFilterModal() {
    this.draftProductFilter.set(this.selectedProductFilter());
    this.draftSourceFilter.set(this.selectedSourceFilter());
    this.draftStatusFilter.set(this.selectedStatusFilter());
    this.draftAssignedUserFilter.set(this.selectedAssignedUserFilter());
    this.showFilterModal.set(true);
  }

  closeFilterModal() {
    this.showFilterModal.set(false);
  }

  clearModalFilters() {
    this.draftProductFilter.set('');
    this.draftSourceFilter.set('');
    this.draftStatusFilter.set('');
    this.draftAssignedUserFilter.set('');
  }

  applyFiltersFromModal() {
    this.selectedProductFilter.set(this.draftProductFilter());
    this.selectedSourceFilter.set(this.draftSourceFilter());
    this.selectedStatusFilter.set(this.draftStatusFilter());
    this.selectedAssignedUserFilter.set(this.draftAssignedUserFilter());
    this.showFilterModal.set(false);
    this.onFilterOrSearchChange();
  }

  // Status and assignment update methods
  updateLeadStatus(
    leadId: string,
    newStatus:
      | 'NEW'
      | 'ASSIGNED'
      | 'CONTACTED'
      | 'PROPOSAL_SENT'
      | 'IN_PROGRESS'
      | 'QUALIFIED'
      | 'CONVERTED'
      | 'NOT_CONVERTED'
      | 'CLOSED',
  ) {
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

  isTerminalState(status: string | undefined): boolean {
    if (!status) return false;
    const s = status.toUpperCase();
    return s === 'CLOSED' || s === 'CONVERTED' || s === 'NOT_CONVERTED';
  }

  getStatusDisplay(status: string | undefined): string {
    if (!status) return 'NEW';
    const s = status.toUpperCase();
    if (s === 'NEW') return 'New';
    if (s === 'ASSIGNED') return 'Assigned';
    if (s === 'CONTACTED') return 'Contacted';
    if (s === 'PROPOSAL_SENT') return 'Proposal sent';
    if (s === 'IN_PROGRESS') return 'In progress (legacy)';
    if (s === 'QUALIFIED') return 'Qualified (legacy)';
    if (s === 'CONVERTED') return 'Converted';
    if (s === 'NOT_CONVERTED') return 'Not converted';
    if (s === 'CLOSED') return 'Closed (legacy)';
    return status;
  }

  getStatusOptions(): Array<{ value: string; label: string }> {
    return [
      { value: 'NEW', label: 'New' },
      { value: 'ASSIGNED', label: 'Assigned' },
      { value: 'CONTACTED', label: 'Contacted' },
      { value: 'PROPOSAL_SENT', label: 'Proposal sent' },
      { value: 'IN_PROGRESS', label: 'In progress (legacy)' },
      { value: 'QUALIFIED', label: 'Qualified (legacy)' },
      { value: 'CONVERTED', label: 'Converted' },
      { value: 'NOT_CONVERTED', label: 'Not converted' },
      { value: 'CLOSED', label: 'Closed (legacy)' },
    ];
  }

  getStatusOptionsForLead(lead: Lead): Array<{ value: string; label: string }> {
    const cur = (lead.status || 'NEW').toString().toUpperCase();
    if (this.isTerminalState(cur)) {
      return [{ value: cur, label: this.getStatusDisplay(cur) }];
    }
    const allowed = lead.allowed_next_states;
    if (allowed == null) {
      return this.getStatusOptions();
    }
    if (allowed.length === 0) {
      return [{ value: cur, label: this.getStatusDisplay(cur) }];
    }
    const values = new Set<string>([cur, ...allowed.map((x) => x.toUpperCase())]);
    return Array.from(values).map((v) => ({ value: v, label: this.getStatusDisplay(v) }));
  }

  statusSelectDisabled(lead: Lead): boolean {
    if (this.updatingLeadId() === lead.lead_id) return true;
    const cur = (lead.status || 'NEW').toString().toUpperCase();
    if (this.isTerminalState(cur)) return true;
    const allowed = lead.allowed_next_states;
    if (allowed != null && allowed.length === 0) return true;
    return false;
  }

  assignmentRowDisabled(lead: Lead): boolean {
    return this.updatingLeadId() === lead.lead_id || this.isTerminalState(String(lead.status));
  }

  getScoreTitle(lead: Lead): string {
    const reason = lead.score_reason || 'Not scored';
    if (lead.score_breakdown && Object.keys(lead.score_breakdown).length > 0) {
      try {
        return `${reason}\n${JSON.stringify(lead.score_breakdown, null, 0)}`;
      } catch {
        return reason;
      }
    }
    return reason;
  }

  openHistoryModal(lead: Lead) {
    this.historyLead.set(lead);
    this.historyData.set(null);
    this.historyError.set(null);
    this.historyLoading.set(true);
    this.leadService.getLeadHistory(lead.lead_id).subscribe({
      next: (d) => {
        this.historyData.set(d);
        this.historyLoading.set(false);
      },
      error: (err) => {
        const msg = err?.message || 'Failed to load history';
        this.historyError.set(msg);
        this.historyLoading.set(false);
        this.toast.error(msg);
      },
    });
  }

  closeHistoryModal() {
    this.historyLead.set(null);
    this.historyData.set(null);
    this.historyError.set(null);
  }

  historyEventsChronological(): LeadHistoryEvent[] {
    const ev = this.historyData()?.events ?? [];
    return [...ev].sort((a, b) => {
      const ta = a.at ? new Date(a.at).getTime() : 0;
      const tb = b.at ? new Date(b.at).getTime() : 0;
      return ta - tb;
    });
  }

  formatHistoryTime(at?: string): string {
    if (!at) return '—';
    try {
      return new Date(at).toLocaleString();
    } catch {
      return at;
    }
  }

  formatHistoryType(type?: string): string {
    if (!type) return 'Event';
    return type
      .split('_')
      .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
      .join(' ');
  }

  formatHistoryPayload(payload: Record<string, unknown> | undefined): string {
    if (!payload || Object.keys(payload).length === 0) return '—';
    try {
      return JSON.stringify(payload);
    } catch {
      return String(payload);
    }
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

  toggleShowTerminalLeads() {
    this.showClosedLeads.update((v) => !v);
    this.currentPage.set(1);
    this.loadLeads();
  }

  /** Call when search or filter changes to refetch page 1 from server */
  onFilterOrSearchChange() {
    this.currentPage.set(1);
    this.loadLeads();
  }

  onLeadStatusChange(leadId: string, value: string) {
    const v = (value || '').toUpperCase();
    const allowed = new Set([
      'NEW',
      'ASSIGNED',
      'CONTACTED',
      'PROPOSAL_SENT',
      'IN_PROGRESS',
      'QUALIFIED',
      'CONVERTED',
      'NOT_CONVERTED',
      'CLOSED',
    ]);
    if (!allowed.has(v)) return;
    this.updateLeadStatus(
      leadId,
      v as
        | 'NEW'
        | 'ASSIGNED'
        | 'CONTACTED'
        | 'PROPOSAL_SENT'
        | 'IN_PROGRESS'
        | 'QUALIFIED'
        | 'CONVERTED'
        | 'NOT_CONVERTED'
        | 'CLOSED',
    );
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

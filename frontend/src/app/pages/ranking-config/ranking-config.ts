import { Component, inject, signal, OnInit, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { LeadService } from '../../services/lead.service';
import { ProductService } from '../../services/product.service';
import { ToastService } from '../../services/toast.service';
import { Product } from '../../models/lead.models';

@Component({
  selector: 'app-ranking-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ranking-config.html',
  styleUrl: './ranking-config.css',
})
export class RankingConfigPage implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly leadService = inject(LeadService);
  private readonly productService = inject(ProductService);
  private readonly toast = inject(ToastService);
  private readonly platformId = inject(PLATFORM_ID);

  readonly mlCanonicalFields = [
    { value: 'income', label: 'Income' },
    { value: 'credit_score', label: 'Credit Score' },
    { value: 'loan_amount', label: 'Loan Amount' },
    { value: 'emp_salaried', label: 'Employment: Salaried' },
    { value: 'emp_self_employed', label: 'Employment: Self-Employed' },
    { value: 'has_email', label: 'Has Email' },
    { value: 'has_phone', label: 'Has Phone' },
    { value: 'days_since_created', label: 'Days Since Created' },
  ] as const;

  mlServiceAvailable = signal(false);
  scoringMethod = signal('Checking...');
  isScoring = signal(false);
  showScoringModal = signal(false);
  scoringResult = signal<{ totalLeads: number; scoredCount: number } | null>(null);
  scoringError = signal<string | null>(null);
  isCheckingStatus = signal(true);

  products = signal<Product[]>([]);
  selectedConfigProduct = signal('');
  selectedCanonicalFields = signal<string[]>([]);
  profileUpdatedAt = signal<string | null>(null);
  loadingProfile = signal(false);
  savingProfile = signal(false);

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.checkMlStatus();
      this.productService.getProducts().subscribe((p) => this.products.set(p));
    }
  }

  isAdmin(): boolean {
    return this.apiService.isAdmin();
  }

  checkMlStatus() {
    this.isCheckingStatus.set(true);
    this.leadService.getMlStatus().subscribe({
      next: (status) => {
        this.mlServiceAvailable.set(status.mlServiceAvailable);
        this.scoringMethod.set(status.scoringMethod);
        this.isCheckingStatus.set(false);
      },
      error: () => {
        this.mlServiceAvailable.set(false);
        this.scoringMethod.set('Unavailable');
        this.isCheckingStatus.set(false);
        this.toast.error('ML service status check failed. Scoring will use heuristic fallback.');
      },
    });
  }

  scoreAllLeads() {
    this.isScoring.set(true);
    this.showScoringModal.set(true);
    this.scoringResult.set(null);
    this.scoringError.set(null);

    this.leadService.scoreAllLeads().subscribe({
      next: (result) => {
        this.isScoring.set(false);
        this.showScoringModal.set(false);
        this.scoringResult.set(result);
      },
      error: (err) => {
        this.isScoring.set(false);
        this.showScoringModal.set(false);
        const msg = err?.message || 'Scoring failed';
        this.scoringError.set(msg);
        this.toast.error(msg);
      },
    });
  }

  closeScoringModal() {
    this.showScoringModal.set(false);
  }

  onProductChange(productId: string) {
    this.selectedConfigProduct.set(productId);
    this.selectedCanonicalFields.set([]);
    this.profileUpdatedAt.set(null);
    if (!productId) return;
    this.loadingProfile.set(true);
    this.productService.getRankingProfile(productId).subscribe({
      next: (profile) => {
        this.selectedCanonicalFields.set(profile.canonicalFields ?? []);
        this.profileUpdatedAt.set(profile.updatedAt ? String(profile.updatedAt) : null);
        this.loadingProfile.set(false);
      },
      error: (err) => {
        this.loadingProfile.set(false);
        this.toast.error(err?.message || 'Failed to load profile');
      },
    });
  }

  toggleCanonicalField(fieldValue: string) {
    this.selectedCanonicalFields.update((fields) => {
      const index = fields.indexOf(fieldValue);
      if (index >= 0) {
        return fields.filter((_, i) => i !== index);
      } else {
        return [...fields, fieldValue];
      }
    });
  }

  isFieldSelected(fieldValue: string): boolean {
    return this.selectedCanonicalFields().includes(fieldValue);
  }

  saveProfile() {
    const pid = this.selectedConfigProduct();
    if (!pid) {
      this.toast.error('Select a product first');
      return;
    }
    this.savingProfile.set(true);
    this.productService
      .saveRankingProfile(pid, {
        pId: pid.toUpperCase(),
        canonicalFields: this.selectedCanonicalFields(),
        rules: [],
      })
      .subscribe({
        next: (saved) => {
          this.savingProfile.set(false);
          this.profileUpdatedAt.set(saved.updatedAt ? String(saved.updatedAt) : null);
          this.toast.success('Ranking configuration saved');
        },
        error: (err) => {
          this.savingProfile.set(false);
          this.toast.error(err?.message || 'Failed to save profile');
        },
      });
  }
}

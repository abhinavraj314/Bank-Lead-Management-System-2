import { Component, OnInit, inject, signal, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { ProductService } from '../../services/product.service';
import { SourceService } from '../../services/source.service';
import {
  ApiResponse,
  AssignmentRuleDto,
  Product,
  Source,
  TeamDto,
  UserResponse,
} from '../../models/lead.models';

type TeamEditRow = {
  id?: string;
  name: string;
  adminUserId: string;
  memberIdsText: string;
};
type RuleEditRow = {
  priority: number;
  productId: string;
  sourceId: string;
  teamId: string;
};

@Component({
  selector: 'app-teams',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './teams.html',
  styleUrl: './teams.css',
})
export class TeamsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly productService = inject(ProductService);
  private readonly sourceService = inject(SourceService);
  private readonly platformId = inject(PLATFORM_ID);

  teams = signal<TeamDto[]>([]);
  teamRows = signal<TeamEditRow[]>([]);
  ruleRows = signal<RuleEditRow[]>([]);
  products = signal<Product[]>([]);
  sources = signal<Source[]>([]);
  users = signal<UserResponse[]>([]); // List of all active users for dropdowns

  loading = signal(false);
  savingTeamId = signal<string | 'new' | null>(null);
  savingRules = signal(false);

  // Create team modal state
  showCreateTeamModal = signal(false);
  newTeamName = '';
  newTeamAdminUserId = '';
  newTeamMemberIds: string[] = [];

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.productService.getProducts().subscribe((p) => this.products.set(p));
      this.sourceService.getSources().subscribe((s) => this.sources.set(s));
      this.loadUsers();
      this.refreshAll();
    }
  }

  /**
   * Load all active users for dropdowns (team admin selection, member selection)
   */
  loadUsers() {
    this.api.get<ApiResponse<UserResponse[]>>('/users/list/all').subscribe({
      next: (r) => {
        this.users.set(r.data ?? []);
      },
      error: () => {
        this.toast.error('Failed to load users');
      },
    });
  }

  isAdmin(): boolean {
    return this.api.isAdmin();
  }

  /**
   * Get user display name by ID (for dropdowns)
   */
  getUserName(userId: string): string {
    const user = this.users().find((u) => u.id === userId);
    return user ? `${user.username} (${user.email})` : userId;
  }

  /**
   * Open modal to create new team
   */
  openCreateTeamModal() {
    this.showCreateTeamModal.set(true);
    this.newTeamName = '';
    this.newTeamAdminUserId = '';
    this.newTeamMemberIds = [];
  }

  /**
   * Close modal without creating team
   */
  closeCreateTeamModal() {
    this.showCreateTeamModal.set(false);
  }

  /**
   * Toggle user in team members selection
   */
  toggleMember(userId: string) {
    const idx = this.newTeamMemberIds.indexOf(userId);
    if (idx >= 0) {
      this.newTeamMemberIds.splice(idx, 1);
    } else {
      this.newTeamMemberIds.push(userId);
    }
  }

  /**
   * Check if a user is selected in members list
   */
  isUserSelected(userId: string): boolean {
    return this.newTeamMemberIds.includes(userId);
  }

  /**
   * Create new team with mandatory admin, optional members
   */
  createTeam() {
    const name = this.newTeamName.trim();
    if (!name) {
      this.toast.error('Team name is required');
      return;
    }
    if (!this.newTeamAdminUserId) {
      this.toast.error('Team admin is required');
      return;
    }

    this.savingTeamId.set('new');
    const payload: any = {
      name,
      adminUserId: this.newTeamAdminUserId,
      memberUserIds: this.newTeamMemberIds,
    };

    this.api.post<ApiResponse<TeamDto>>('/teams', payload).subscribe({
      next: (r) => {
        this.savingTeamId.set(null);
        if (!r.success) {
          this.toast.error(r.message || 'Failed to create team');
          return;
        }
        this.toast.success('Team created');
        this.closeCreateTeamModal();
        this.refreshAll();
      },
      error: (e) => {
        this.savingTeamId.set(null);
        this.toast.error(e?.error?.message || e?.message || 'Failed to create team');
      },
    });
  }

  saveTeamRow(row: TeamEditRow) {
    if (!row.id) return;
    const name = row.name.trim();
    if (!name) {
      this.toast.error('Team name is required');
      return;
    }
    if (!row.adminUserId) {
      this.toast.error('Team admin is required');
      return;
    }

    const memberUserIds = row.memberIdsText
      .split(/[\s,;]+/)
      .map((s) => s.trim())
      .filter(Boolean);

    this.savingTeamId.set(row.id);
    const payload = {
      name,
      adminUserId: row.adminUserId,
      memberUserIds,
    };

    this.api.put<ApiResponse<TeamDto>>(`/teams/${row.id}`, payload).subscribe({
      next: (r) => {
        this.savingTeamId.set(null);
        if (!r.success) {
          this.toast.error(r.message || 'Failed to update team');
          return;
        }
        this.toast.success('Team updated');
        this.refreshAll();
      },
      error: (e) => {
        this.savingTeamId.set(null);
        this.toast.error(e?.error?.message || e?.message || 'Failed to update team');
      },
    });
  }

  deleteTeam(id: string) {
    if (!confirm('Delete this team? Assignment rules referencing it may need to be updated.'))
      return;
    this.api.delete<ApiResponse<unknown>>(`/teams/${id}`).subscribe({
      next: (r) => {
        if (!r.success) {
          this.toast.error(r.message || 'Delete failed');
          return;
        }
        this.toast.success('Team deleted');
        this.refreshAll();
      },
      error: (e) => this.toast.error(e?.error?.message || e?.message || 'Delete failed'),
    });
  }

  refreshAll() {
    this.loading.set(true);
    this.api.get<ApiResponse<TeamDto[]>>('/teams').subscribe({
      next: (r) => {
        const data = r.data ?? [];
        this.teams.set(data);
        this.teamRows.set(
          data.map((t) => ({
            id: t.id,
            name: t.name || '',
            adminUserId: t.adminUserId || '',
            memberIdsText: (t.memberUserIds || []).join(', '),
          })),
        );
        this.loading.set(false);
      },
      error: () => {
        this.toast.error('Failed to load teams');
        this.loading.set(false);
      },
    });

    this.api.get<ApiResponse<AssignmentRuleDto[]>>('/assignment-rules').subscribe({
      next: (r) => {
        const list = r.data ?? [];
        this.ruleRows.set(
          list.map((x) => ({
            priority: x.priority ?? 0,
            productId: x.productId?.trim() ?? '',
            sourceId: x.sourceId?.trim() ?? '',
            teamId: x.teamId ?? '',
          })),
        );
      },
      error: () => this.toast.error('Failed to load assignment rules'),
    });
  }

  addRuleRow() {
    const nextPri =
      this.ruleRows().length === 0 ? 0 : Math.max(...this.ruleRows().map((x) => x.priority)) + 1;
    this.ruleRows.update((list) => [
      ...list,
      { priority: nextPri, productId: '', sourceId: '', teamId: '' },
    ]);
  }

  removeRuleRow(index: number) {
    this.ruleRows.update((list) => list.filter((_, i) => i !== index));
  }

  saveRules() {
    const body = this.ruleRows().map((r) => ({
      priority: Number(r.priority) || 0,
      productId: r.productId?.trim() ? r.productId.trim() : null,
      sourceId: r.sourceId?.trim() ? r.sourceId.trim() : null,
      teamId: r.teamId?.trim() || '',
    }));
    for (const r of body) {
      if (!r.teamId) {
        this.toast.error('Each rule must select a team');
        return;
      }
    }
    this.savingRules.set(true);
    this.api.put<ApiResponse<AssignmentRuleDto[]>>('/assignment-rules', body).subscribe({
      next: (r) => {
        this.savingRules.set(false);
        if (!r.success) {
          this.toast.error(r.message || 'Failed to save rules');
          return;
        }
        const list = r.data ?? [];
        this.ruleRows.set(
          list.map((x) => ({
            priority: x.priority ?? 0,
            productId: x.productId?.trim() ?? '',
            sourceId: x.sourceId?.trim() ?? '',
            teamId: x.teamId ?? '',
          })),
        );
        this.toast.success('Assignment rules saved');
      },
      error: (e) => {
        this.savingRules.set(false);
        this.toast.error(e?.error?.message || e?.message || 'Failed to save rules');
      },
    });
  }

  sourcesForProduct(productId: string): Source[] {
    if (!productId) return this.sources();
    return this.sources().filter((s) => s.product_id === productId);
  }
}

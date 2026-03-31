import { Component, OnInit, signal, inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CanonicalFieldService } from '../../services/canonical-field.service';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { CanonicalField } from '../../models/lead.models';

@Component({
  selector: 'app-canonical-fields',
  imports: [CommonModule, FormsModule],
  templateUrl: './canonical-fields.html',
  styleUrl: './canonical-fields.css',
})
export class CanonicalFieldsPage implements OnInit {
  private readonly canonicalFieldService = inject(CanonicalFieldService);
  private readonly apiService = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly fields = signal<CanonicalField[]>([]);
  protected readonly showCreateModal = signal<boolean>(false);
  protected readonly isCreating = signal<boolean>(false);
  protected readonly errorMessage = signal<string>('');

  // Form fields (using regular properties for ngModel binding)
  protected newField: Partial<CanonicalField> = {
    field_name: '',
    display_name: '',
    type: 'String',
  };

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.loadFields();
    }
  }

  isAdmin(): boolean {
    return this.apiService.isAdmin();
  }

  loadFields(): void {
    this.canonicalFieldService.getCanonicalFields().subscribe({
      next: (fields) => {
        this.fields.set(fields);
      },
      error: (error) => {
        const msg = error?.message || 'Failed to load canonical fields';
        this.toast.error(msg);
      },
    });
  }

  openCreateModal(): void {
    this.showCreateModal.set(true);
    this.errorMessage.set('');
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
    this.errorMessage.set('');
    this.isCreating.set(false);
    this.newField = {
      field_name: '',
      display_name: '',
      type: 'String',
    };
  }

  onCreateField(): void {
    // Validation
    if (!this.newField.field_name?.trim()) {
      this.errorMessage.set('Field Name is required');
      this.toast.error('Field Name is required');
      return;
    }
    if (!this.newField.display_name?.trim()) {
      this.errorMessage.set('Display Name is required');
      this.toast.error('Display Name is required');
      return;
    }
    if (!this.newField.type) {
      this.errorMessage.set('Type is required');
      this.toast.error('Type is required');
      return;
    }

    // Check if field name already exists (client-side check)
    const existingField = this.fields().find(
      (f) => f.field_name.toLowerCase() === this.newField.field_name!.toLowerCase(),
    );
    if (existingField) {
      this.errorMessage.set(`Field '${this.newField.field_name}' already exists`);
      this.toast.error(`Field '${this.newField.field_name}' already exists`);
      return;
    }

    // Only allow String, Number, Date, Boolean (remove Email and Phone)
    const allowedTypes: string[] = ['String', 'Number', 'Date', 'Boolean'];
    if (!allowedTypes.includes(this.newField.type)) {
      this.errorMessage.set('Type must be String, Number, Date, or Boolean');
      this.toast.error('Type must be String, Number, Date, or Boolean');
      return;
    }

    this.isCreating.set(true);
    this.errorMessage.set('');

    this.canonicalFieldService
      .createCanonicalField({
        field_name: this.newField.field_name.trim(),
        display_name: this.newField.display_name.trim(),
        field_type: this.newField.type as 'String' | 'Number' | 'Date' | 'Boolean',
        // Status is fixed for new canonical fields (no status picker in UI).
        // If you need inactive fields later, we can add that back explicitly.
        is_active: true,
        version: 'v1',
      })
      .subscribe({
        next: () => {
          this.loadFields(); // Reload from backend
          this.closeCreateModal();
        },
        error: (error) => {
          this.isCreating.set(false);
          const msg = error.message || 'Failed to create canonical field';
          this.errorMessage.set(msg);
          this.toast.error(msg);
        },
      });
  }
}

import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../../services/product.service';
import { ProductWithStatus } from '../../models/lead.models';

@Component({
  selector: 'app-products',
  imports: [CommonModule, FormsModule],
  templateUrl: './products.html',
  styleUrl: './products.css'
})
export class ProductsPage implements OnInit {
  private readonly productService = inject(ProductService);
  
  protected readonly products = signal<ProductWithStatus[]>([]);
  protected readonly showCreateModal = signal<boolean>(false);
  protected newProductName = '';

  ngOnInit(): void {
    this.loadProducts();
  }

  loadProducts(): void {
    this.productService.getProducts().subscribe(products => {
      // Add mock status and created_date
      const productsWithStatus: ProductWithStatus[] = products.map((p, index) => ({
        ...p,
        status: index % 3 === 0 ? 'inactive' : 'active',
        created_date: new Date(2024, 0, 15 - index).toISOString().split('T')[0]
      }));
      this.products.set(productsWithStatus);
    });
  }

  openCreateModal(): void {
    this.showCreateModal.set(true);
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
    this.newProductName = '';
  }

  onCreateProduct(): void {
    if (!this.newProductName.trim()) return;
    
    // Mock: Add to local state
    const product: ProductWithStatus = {
      product_id: `prod_${Date.now()}`,
      product_name: this.newProductName,
      status: 'active',
      created_date: new Date().toISOString().split('T')[0]
    };
    
    this.products.set([...this.products(), product]);
    this.closeCreateModal();
  }
}

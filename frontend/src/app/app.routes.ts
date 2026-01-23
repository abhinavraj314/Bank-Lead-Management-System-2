import { Routes } from '@angular/router';
import { AdminLayout } from './components/admin-layout/admin-layout';
import { Dashboard } from './pages/dashboard/dashboard';
import { CanonicalFieldsPage } from './pages/canonical-fields/canonical-fields';
import { ProductsPage } from './pages/products/products';
import { SourcesPage } from './pages/sources/sources';
import { DeduplicationRulesPage } from './pages/deduplication-rules/deduplication-rules';
import { RankingConfigPage } from './pages/ranking-config/ranking-config';
import { Leads } from './pages/leads/leads';

export const routes: Routes = [
  {
    path: 'admin',
    component: AdminLayout,
    children: [
      { path: 'dashboard', component: Dashboard },
      { path: 'leads', component: Leads },
      { path: 'canonical-fields', component: CanonicalFieldsPage },
      { path: 'products', component: ProductsPage },
      { path: 'sources', component: SourcesPage },
      { path: 'deduplication-rules', component: DeduplicationRulesPage },
      { path: 'ranking-config', component: RankingConfigPage },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '', redirectTo: '/admin/dashboard', pathMatch: 'full' }
];

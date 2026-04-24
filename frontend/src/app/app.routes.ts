import { Routes } from '@angular/router';
import { AdminLayout } from './components/admin-layout/admin-layout';
import { AuthComponent } from './pages/auth/auth';
import { Dashboard } from './pages/dashboard/dashboard';
import { CanonicalFieldsPage } from './pages/canonical-fields/canonical-fields';
import { ProductsPage } from './pages/products/products';
import { SourcesPage } from './pages/sources/sources';
import { DeduplicationRulesPage } from './pages/deduplication-rules/deduplication-rules';
import { RankingConfigPage } from './pages/ranking-config/ranking-config';
import { Leads } from './pages/leads/leads';
import { UsersPage } from './pages/users/users';
import { TeamsPage } from './pages/teams/teams';
import { AuthGuard } from './services/auth.guard';
import { AcceptInviteComponent } from './pages/auth/accept-invite/accept-invite';
import { ReportsPage } from './pages/reports/reports';

export const routes: Routes = [
  { path: 'auth', component: AuthComponent },
  { path: 'auth/accept-invite', component: AcceptInviteComponent },
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [AuthGuard],
    children: [
      { path: 'dashboard', component: Dashboard },
      { path: 'leads', component: Leads },
      { path: 'canonical-fields', component: CanonicalFieldsPage },
      { path: 'products', component: ProductsPage },
      { path: 'sources', component: SourcesPage },
      { path: 'reports', component: ReportsPage },
      { path: 'deduplication-rules', component: DeduplicationRulesPage },
      { path: 'ranking-config', component: RankingConfigPage },
      { path: 'users', component: UsersPage, data: { adminOnly: true } },
      { path: 'teams', component: TeamsPage, data: { adminOnly: true } },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '', redirectTo: '/auth', pathMatch: 'full' },
  { path: '**', redirectTo: '/auth' },
];

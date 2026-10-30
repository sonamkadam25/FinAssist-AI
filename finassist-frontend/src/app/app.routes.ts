import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then((module) => module.DashboardComponent),
    title: 'Dashboard · FinAssist AI'
  },
  {
    path: 'transactions',
    loadComponent: () => import('./features/transactions/transactions.component').then((module) => module.TransactionsComponent),
    title: 'Transactions · FinAssist AI'
  },
  {
    path: 'assistant',
    loadComponent: () => import('./features/assistant/assistant.component').then((module) => module.AssistantComponent),
    title: 'AI Assistant · FinAssist AI'
  },
  { path: '**', redirectTo: 'dashboard' }
];

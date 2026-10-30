import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription, forkJoin } from 'rxjs';
import { ApiErrorService } from '../../core/services/api-error.service';
import { TransactionService } from '../../core/services/transaction.service';
import { Transaction, TransactionPage, TransactionSummary } from '../../core/models/transaction.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly transactionsApi = inject(TransactionService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly subscriptions = new Subscription();

  summary: TransactionSummary | null = null;
  recent: Transaction[] = [];
  loading = true;
  errorMessage = '';

  ngOnInit(): void {
    this.load();
    this.subscriptions.add(this.transactionsApi.changed$.subscribe(() => this.load()));
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.subscriptions.add(forkJoin({
      summary: this.transactionsApi.summary(),
      page: this.transactionsApi.list({ page: 0, size: 5 })
    }).subscribe({
      next: (result: { summary: TransactionSummary; page: TransactionPage }) => {
        this.summary = result.summary;
        this.recent = result.page.content;
        this.loading = false;
      },
      error: (error: unknown) => {
        this.errorMessage = this.apiErrors.message(error, 'Dashboard data could not be loaded.');
        this.loading = false;
      }
    }));
  }
}

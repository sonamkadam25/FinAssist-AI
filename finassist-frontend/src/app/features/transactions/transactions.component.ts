import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { Subject, Subscription, catchError, debounceTime, distinctUntilChanged, of, startWith, switchMap } from 'rxjs';
import {
  Transaction,
  TransactionFilters,
  TransactionInput,
  TransactionPage,
  TransactionStatus,
  TransactionType
} from '../../core/models/transaction.models';
import { ApiErrorService } from '../../core/services/api-error.service';
import { TransactionService } from '../../core/services/transaction.service';

@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule],
  templateUrl: './transactions.component.html'
})
export class TransactionsComponent implements OnInit, OnDestroy {
  private readonly formBuilder = inject(FormBuilder);
  private readonly transactionsApi = inject(TransactionService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly route = inject(ActivatedRoute);
  private readonly subscriptions = new Subscription();
  private readonly pageRequests = new Subject<TransactionFilters>();

  readonly statuses: TransactionStatus[] = ['PENDING', 'COMPLETED', 'FAILED'];
  readonly filtersForm = this.formBuilder.group({
    transactionType: ['' as TransactionType | ''],
    status: ['' as TransactionStatus | ''],
    category: [''],
    minAmount: [''],
    maxAmount: [''],
    startDate: [''],
    endDate: ['']
  });
  readonly transactionForm = this.formBuilder.group({
    transactionType: ['EXPENSE' as TransactionType, Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(0.01), Validators.max(99999999999999999), Validators.pattern(/^\d+(\.\d{1,2})?$/)]],
    category: ['', [Validators.required, Validators.pattern(/.*\S.*/), Validators.maxLength(50)]],
    merchant: ['', [Validators.required, Validators.pattern(/.*\S.*/), Validators.maxLength(100)]],
    description: ['', Validators.maxLength(500)],
    transactionDate: ['', Validators.required],
    status: ['COMPLETED' as TransactionStatus, Validators.required]
  });

  page: TransactionPage | null = null;
  currentPage = 0;
  pageSize = 10;
  loading = true;
  saving = false;
  deletingId: number | null = null;
  modalOpen = false;
  editing: Transaction | null = null;
  errorMessage = '';
  successMessage = '';

  ngOnInit(): void {
    this.subscriptions.add(this.pageRequests.pipe(
      switchMap((filters: TransactionFilters) => {
        this.loading = true;
        this.errorMessage = '';
        return this.transactionsApi.list(filters).pipe(
          catchError((error: unknown) => {
            this.page = null;
            this.errorMessage = this.apiErrors.message(error, 'Transactions could not be loaded.');
            return of(null);
          })
        );
      })
    ).subscribe((page: TransactionPage | null) => {
      this.page = page;
      this.loading = false;
    }));

    this.subscriptions.add(this.filtersForm.valueChanges.pipe(
      startWith(this.filtersForm.getRawValue()),
      debounceTime(250),
      distinctUntilChanged((first, second) => JSON.stringify(first) === JSON.stringify(second))
    ).subscribe(() => {
      this.currentPage = 0;
      this.requestPage();
    }));

    this.subscriptions.add(this.route.queryParamMap.subscribe((params: ParamMap) => {
      if (params.get('action') === 'create') {
        this.openCreate();
      }
    }));
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  load(): void {
    this.requestPage();
  }

  private requestPage(): void {
    this.pageRequests.next(this.currentFilters());
  }

  private currentFilters(): TransactionFilters {
    const values = this.filtersForm.getRawValue();
    const minAmount = values.minAmount ? Number(values.minAmount) : undefined;
    const maxAmount = values.maxAmount ? Number(values.maxAmount) : undefined;
    return {
      transactionType: values.transactionType ?? '',
      status: values.status ?? '',
      category: values.category?.trim() || undefined,
      minAmount,
      maxAmount,
      startDate: values.startDate || undefined,
      endDate: values.endDate || undefined,
      page: this.currentPage,
      size: this.pageSize
    };
  }

  goToPage(page: number): void {
    if (page < 0 || (this.page && page >= this.page.totalPages)) return;
    this.currentPage = page;
    this.load();
  }

  clearFilters(): void {
    this.filtersForm.reset({
      transactionType: '',
      status: '',
      category: '',
      minAmount: '',
      maxAmount: '',
      startDate: '',
      endDate: ''
    });
  }

  openCreate(): void {
    this.editing = null;
    this.successMessage = '';
    this.transactionForm.reset({
      transactionType: 'EXPENSE',
      amount: null,
      category: '',
      merchant: '',
      description: '',
      transactionDate: this.toLocalInputValue(new Date()),
      status: 'COMPLETED'
    });
    this.modalOpen = true;
  }

  openEdit(transaction: Transaction): void {
    this.editing = transaction;
    this.successMessage = '';
    this.transactionForm.reset({
      transactionType: transaction.transactionType,
      amount: transaction.amount,
      category: transaction.category,
      merchant: transaction.merchant,
      description: transaction.description ?? '',
      transactionDate: transaction.transactionDate.slice(0, 16),
      status: transaction.status
    });
    this.modalOpen = true;
  }

  closeModal(): void {
    if (this.saving) return;
    this.modalOpen = false;
    this.editing = null;
  }

  save(): void {
    this.transactionForm.markAllAsTouched();
    if (this.transactionForm.invalid || this.saving) return;

    const form = this.transactionForm.getRawValue();
    if (!form.transactionType || form.amount === null || !form.category || !form.merchant
      || !form.transactionDate || !form.status) {
      return;
    }
    const input: TransactionInput = {
      transactionType: form.transactionType,
      amount: Number(form.amount),
      category: form.category.trim(),
      merchant: form.merchant.trim(),
      description: form.description?.trim() || null,
      transactionDate: form.transactionDate,
      status: form.status
    };
    const operation = this.editing
      ? this.transactionsApi.update(this.editing.id, input)
      : this.transactionsApi.create(input);
    this.saving = true;
    this.errorMessage = '';
    this.subscriptions.add(operation.subscribe({
      next: () => {
        this.saving = false;
        this.modalOpen = false;
        this.editing = null;
        this.successMessage = 'Transaction saved successfully.';
        this.transactionsApi.notifyChanged();
        this.load();
      },
      error: (error: unknown) => {
        this.saving = false;
        this.errorMessage = this.apiErrors.message(error, 'Transaction could not be saved.');
      }
    }));
  }

  remove(transaction: Transaction): void {
    if (this.deletingId !== null) return;
    const confirmed = window.confirm(`Delete the ${transaction.transactionType.toLowerCase()} transaction from ${transaction.merchant}? This cannot be undone.`);
    if (!confirmed) return;
    this.errorMessage = '';
    this.successMessage = '';
    this.deletingId = transaction.id;
    this.subscriptions.add(this.transactionsApi.delete(transaction.id).subscribe({
      next: () => {
        this.deletingId = null;
        this.successMessage = 'Transaction deleted.';
        if (this.page?.content.length === 1 && this.currentPage > 0) this.currentPage -= 1;
        this.transactionsApi.notifyChanged();
        this.load();
      },
      error: (error: unknown) => {
        this.deletingId = null;
        this.errorMessage = this.apiErrors.message(error, 'Transaction could not be deleted.');
      }
    }));
  }

  private toLocalInputValue(date: Date): string {
    const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return localDate.toISOString().slice(0, 16);
  }
}

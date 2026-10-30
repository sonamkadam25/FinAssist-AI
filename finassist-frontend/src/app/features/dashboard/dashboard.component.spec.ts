import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { TransactionPage, TransactionSummary } from '../../core/models/transaction.models';
import { TransactionService } from '../../core/services/transaction.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let transactions: jasmine.SpyObj<TransactionService>;

  const summary: TransactionSummary = {
    totalIncome: 1250,
    totalExpenses: 340.5,
    netBalance: 909.5,
    totalTransactions: 4,
    completedTransactions: 3,
    pendingTransactions: 1,
    failedTransactions: 0,
    expenseTotalsByCategory: [],
    incomeTotalsByCategory: []
  };
  const page: TransactionPage = {
    content: [], page: 0, size: 5, totalElements: 0, totalPages: 0, last: true
  };

  beforeEach(async () => {
    const changed = new Subject<void>();
    transactions = jasmine.createSpyObj<TransactionService>(
      'TransactionService',
      ['list', 'summary'],
      { changed$: changed.asObservable() }
    );
    transactions.summary.and.returnValue(of(summary));
    transactions.list.and.returnValue(of(page));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideRouter([]), { provide: TransactionService, useValue: transactions }]
    }).compileComponents();
    fixture = TestBed.createComponent(DashboardComponent);
  });

  it('renders dashboard totals from backend summary data', () => {
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Total transactions');
    expect(text).toContain('4');
    expect(text).toContain('340.50');
    expect(transactions.summary).toHaveBeenCalled();
    expect(transactions.list).toHaveBeenCalledWith({ page: 0, size: 5 });
  });

  it('shows loading placeholders until backend summary and list responses arrive', () => {
    const summaryResponse = new Subject<TransactionSummary>();
    const pageResponse = new Subject<TransactionPage>();
    transactions.summary.and.returnValue(summaryResponse);
    transactions.list.and.returnValue(pageResponse);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.skeleton-card').length).toBe(4);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Loading transactions');

    summaryResponse.next(summary);
    summaryResponse.complete();
    pageResponse.next(page);
    pageResponse.complete();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.skeleton-card').length).toBe(0);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Total expenses');
  });

  it('shows a real-data empty state when backend summary is empty', () => {
    transactions.summary.and.returnValue(of({
      ...summary, totalIncome: 0, totalExpenses: 0, netBalance: 0,
      totalTransactions: 0, completedTransactions: 0
    }));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Your transaction overview starts here');
  });

  it('shows a retryable error when the backend cannot be reached', () => {
    transactions.summary.and.returnValue(throwError(() => new Error('offline')));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Dashboard data could not be loaded.');
    expect((fixture.nativeElement as HTMLElement).querySelector('button')?.textContent).toContain('Try again');
  });
});

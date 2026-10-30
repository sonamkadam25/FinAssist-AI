import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TransactionInput, TransactionPage } from '../models/transaction.models';
import { TransactionService } from './transaction.service';

describe('TransactionService', () => {
  let service: TransactionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(TransactionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('serializes supported filters and pagination as query parameters', () => {
    service.list({
      transactionType: 'EXPENSE',
      status: 'COMPLETED',
      category: 'FOOD',
      minAmount: 10,
      maxAmount: 500,
      startDate: '2026-10-01T00:00',
      page: 2,
      size: 10
    }).subscribe((response: TransactionPage) => expect(response.totalElements).toBe(1));

    const request = http.expectOne((candidate) =>
      candidate.url === '/api/transactions'
      && candidate.params.get('transactionType') === 'EXPENSE'
      && candidate.params.get('status') === 'COMPLETED'
      && candidate.params.get('category') === 'FOOD'
      && candidate.params.get('minAmount') === '10'
      && candidate.params.get('maxAmount') === '500'
      && candidate.params.get('startDate') === '2026-10-01T00:00'
      && candidate.params.get('page') === '2'
      && candidate.params.get('size') === '10'
    );
    expect(request.request.method).toBe('GET');
    request.flush({ content: [], page: 2, size: 10, totalElements: 1, totalPages: 1, last: true });
  });

  it('uses the existing create, update, and delete contracts', () => {
    const input: TransactionInput = {
      transactionType: 'EXPENSE',
      amount: 25.5,
      category: 'FOOD',
      merchant: 'Demo Cafe',
      description: null,
      transactionDate: '2026-10-30T12:30',
      status: 'COMPLETED'
    };

    service.create(input).subscribe();
    const createRequest = http.expectOne('/api/transactions');
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual(input);
    createRequest.flush({ id: 8 });

    service.update(8, input).subscribe();
    const updateRequest = http.expectOne('/api/transactions/8');
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual(input);
    updateRequest.flush({ id: 8 });

    service.delete(8).subscribe();
    const deleteRequest = http.expectOne('/api/transactions/8');
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null);
  });

  it('uses the summary route and ISO local date parameters', () => {
    service.summary('2026-10-01T00:00:00', '2026-10-31T23:59:59').subscribe();
    const request = http.expectOne((candidate) =>
      candidate.url === '/api/transactions/summary'
      && candidate.params.get('startDate') === '2026-10-01T00:00:00'
      && candidate.params.get('endDate') === '2026-10-31T23:59:59'
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      totalIncome: 0, totalExpenses: 0, netBalance: 0, totalTransactions: 0,
      completedTransactions: 0, pendingTransactions: 0, failedTransactions: 0,
      expenseTotalsByCategory: [], incomeTotalsByCategory: []
    });
  });
});

import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { Transaction, TransactionInput, TransactionPage } from '../../core/models/transaction.models';
import { TransactionService } from '../../core/services/transaction.service';
import { TransactionsComponent } from './transactions.component';

describe('TransactionsComponent', () => {
  let fixture: ComponentFixture<TransactionsComponent>;
  let transactions: jasmine.SpyObj<TransactionService>;
  let transaction: Transaction;

  const page: TransactionPage = {
    content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true
  };

  beforeEach(async () => {
    transaction = {
      id: 21,
      transactionType: 'EXPENSE',
      amount: 25.5,
      category: 'FOOD',
      merchant: 'Demo Cafe',
      description: 'Lunch',
      transactionDate: '2026-10-30T12:30:00',
      status: 'COMPLETED',
      createdAt: '2026-10-30T12:31:00'
    };
    const changed = new Subject<void>();
    transactions = jasmine.createSpyObj<TransactionService>(
      'TransactionService',
      ['list', 'create', 'update', 'delete', 'notifyChanged'],
      { changed$: changed.asObservable() }
    );
    transactions.list.and.returnValue(of(page));
    transactions.create.and.returnValue(of(transaction));
    transactions.update.and.returnValue(of(transaction));
    transactions.delete.and.returnValue(of(void 0));

    await TestBed.configureTestingModule({
      imports: [TransactionsComponent],
      providers: [
        provideRouter([]),
        { provide: TransactionService, useValue: transactions },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(new Map()) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(TransactionsComponent);
  });

  it('loads real transaction pages and shows an empty state', fakeAsync(() => {
    fixture.detectChanges();
    tick(250);
    fixture.detectChanges();

    expect(transactions.list).toHaveBeenCalledWith(jasmine.objectContaining({ page: 0, size: 10 }));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No transactions yet');
  }));

  it('prevents invalid transaction submissions', () => {
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.openCreate();
    component.save();

    expect(component.transactionForm.invalid).toBeTrue();
    expect(transactions.create).not.toHaveBeenCalled();
  });

  it('creates a valid transaction and refreshes the dashboard after success', () => {
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.transactionForm.setValue({
      transactionType: 'EXPENSE',
      amount: 25.5,
      category: 'FOOD',
      merchant: 'Demo Cafe',
      description: 'Lunch',
      transactionDate: '2026-10-30T12:30',
      status: 'COMPLETED'
    });
    component.save();

    expect(transactions.create).toHaveBeenCalledWith(jasmine.objectContaining({
      transactionType: 'EXPENSE', amount: 25.5, category: 'FOOD', merchant: 'Demo Cafe'
    } as TransactionInput));
    expect(transactions.notifyChanged).toHaveBeenCalled();
    expect(component.successMessage).toBe('Transaction saved successfully.');
    expect(component.modalOpen).toBeFalse();
  });

  it('updates an existing transaction using the full PUT request', () => {
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.openEdit(transaction);
    component.transactionForm.patchValue({ amount: 30 });
    component.save();

    expect(transactions.update).toHaveBeenCalledWith(21, jasmine.objectContaining({ amount: 30 }));
    expect(transactions.notifyChanged).toHaveBeenCalled();
  });

  it('confirms deletion before calling the backend', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    fixture.detectChanges();
    fixture.componentInstance.remove(transaction);
    expect(transactions.delete).not.toHaveBeenCalled();

    (window.confirm as jasmine.Spy).and.returnValue(true);
    fixture.componentInstance.remove(transaction);
    expect(transactions.delete).toHaveBeenCalledWith(21);
  });
});

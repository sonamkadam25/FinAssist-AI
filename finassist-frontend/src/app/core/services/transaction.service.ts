import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Transaction,
  TransactionFilters,
  TransactionInput,
  TransactionPage,
  TransactionSummary
} from '../models/transaction.models';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly baseUrl = `${environment.apiBaseUrl}/transactions`;
  private readonly changedSubject = new Subject<void>();
  readonly changed$ = this.changedSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  list(filters: TransactionFilters = {}): Observable<TransactionPage> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<TransactionPage>(this.baseUrl, { params });
  }

  get(id: number): Observable<Transaction> {
    return this.http.get<Transaction>(`${this.baseUrl}/${id}`);
  }

  create(input: TransactionInput): Observable<Transaction> {
    return this.http.post<Transaction>(this.baseUrl, input);
  }

  update(id: number, input: TransactionInput): Observable<Transaction> {
    return this.http.put<Transaction>(`${this.baseUrl}/${id}`, input);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  summary(startDate?: string, endDate?: string): Observable<TransactionSummary> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get<TransactionSummary>(`${this.baseUrl}/summary`, { params });
  }

  notifyChanged(): void {
    this.changedSubject.next();
  }
}

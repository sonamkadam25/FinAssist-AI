import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AskResponse } from '../models/transaction.models';
import { AiAssistantService } from './ai-assistant.service';

describe('AiAssistantService', () => {
  let service: AiAssistantService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AiAssistantService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the question to the actual ask endpoint and returns structured evidence', () => {
    service.ask('How much did I spend this month?').subscribe((response: AskResponse) => {
      expect(response.question).toBe('How much did I spend this month?');
      expect(response.data?.summary?.statistics.totalExpenses).toBe(25);
    });

    const request = http.expectOne('/api/ask');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ question: 'How much did I spend this month?' });
    request.flush({
      question: 'How much did I spend this month?',
      answer: 'Backend summary: 25.00 in completed expenses.',
      data: {
        summary: {
          startDate: '2026-10-01T00:00:00',
          endDate: '2026-10-31T23:59:59.999999',
          statistics: {
            totalIncome: 0, totalExpenses: 25, netBalance: -25, totalTransactions: 1,
            completedTransactions: 1, pendingTransactions: 0, failedTransactions: 0,
            expenseTotalsByCategory: [], incomeTotalsByCategory: []
          }
        },
        search: null
      }
    });
  });
});

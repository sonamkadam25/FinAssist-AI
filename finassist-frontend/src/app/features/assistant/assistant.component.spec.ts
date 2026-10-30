import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AiAssistantService } from '../../core/services/ai-assistant.service';
import { AssistantComponent } from './assistant.component';

describe('AssistantComponent', () => {
  let fixture: ComponentFixture<AssistantComponent>;
  let assistant: jasmine.SpyObj<AiAssistantService>;

  beforeEach(async () => {
    assistant = jasmine.createSpyObj<AiAssistantService>('AiAssistantService', ['ask']);
    assistant.ask.and.returnValue(of({
      question: 'How many transactions are recorded?',
      answer: 'The backend summary reports four transactions.',
      data: {
        summary: {
          startDate: null,
          endDate: null,
          statistics: {
            totalIncome: 0, totalExpenses: 0, netBalance: 0, totalTransactions: 4,
            completedTransactions: 4, pendingTransactions: 0, failedTransactions: 0,
            expenseTotalsByCategory: [], incomeTotalsByCategory: []
          }
        },
        search: null
      }
    }));

    await TestBed.configureTestingModule({
      imports: [AssistantComponent],
      providers: [{ provide: AiAssistantService, useValue: assistant }]
    }).compileComponents();
    fixture = TestBed.createComponent(AssistantComponent);
  });

  it('sends the real question and displays the returned answer and structured evidence', () => {
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.question.setValue('How many transactions are recorded?');
    component.submit();
    fixture.detectChanges();

    expect(assistant.ask).toHaveBeenCalledWith('How many transactions are recorded?');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('The backend summary reports four transactions.');
    expect(text).toContain('Backend summary');
    expect(text).toContain('4');
  });

  it('shows an error and allows retry without duplicating the user message', () => {
    assistant.ask.and.returnValues(
      throwError(() => new Error('unavailable')),
      of({ question: 'What is my balance?', answer: 'That data is not available.', data: null })
    );
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.question.setValue('What is my balance?');
    component.submit();
    fixture.detectChanges();
    expect(component.messages.filter((message) => message.role === 'user').length).toBe(1);
    expect(component.messages.at(-1)?.failed).toBeTrue();

    component.retry();
    fixture.detectChanges();
    expect(assistant.ask).toHaveBeenCalledTimes(2);
    expect(component.messages.filter((message) => message.role === 'user').length).toBe(1);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('That data is not available.');
  });

  it('does not send an empty question', () => {
    fixture.detectChanges();
    fixture.componentInstance.submit('  ');
    expect(assistant.ask).not.toHaveBeenCalled();
  });
});

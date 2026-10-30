import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, ElementRef, ViewChild, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiErrorService } from '../../core/services/api-error.service';
import { AiAssistantService } from '../../core/services/ai-assistant.service';
import { AskResponse } from '../../core/models/transaction.models';
import { timeout } from 'rxjs';

interface ConversationMessage {
  role: 'user' | 'assistant';
  text: string;
  response?: AskResponse;
  failed?: boolean;
}

@Component({
  selector: 'app-assistant',
  standalone: true,
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule],
  templateUrl: './assistant.component.html'
})
export class AssistantComponent {
  private readonly assistantApi = inject(AiAssistantService);
  private readonly apiErrors = inject(ApiErrorService);
  @ViewChild('questionInput') private questionInput?: ElementRef<HTMLTextAreaElement>;

  readonly question = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly suggestions = [
    'How many transactions are recorded?',
    'How much did I spend this month?',
    'Show my completed transactions',
    'Find transactions with an amount of at least 5000'
  ];
  messages: ConversationMessage[] = [];
  loading = false;
  errorMessage = '';
  private lastQuestion = '';

  submit(question = this.question.value): void {
    const text = question.trim();
    if (!text || this.loading) {
      this.question.markAsTouched();
      return;
    }

    this.errorMessage = '';
    this.lastQuestion = text;
    this.messages = [...this.messages, { role: 'user', text }];
    this.question.setValue('');
    this.requestAnswer(text);
  }

  private requestAnswer(text: string): void {
    this.loading = true;
    this.assistantApi.ask(text).pipe(timeout({ first: 120_000 })).subscribe({
      next: (response: AskResponse) => {
        this.messages = [...this.messages, { role: 'assistant', text: response.answer, response }];
        this.loading = false;
      },
      error: (error: unknown) => {
        this.errorMessage = this.apiErrors.message(error, 'The assistant could not answer right now.');
        this.messages = [...this.messages, { role: 'assistant', text: this.errorMessage, failed: true }];
        this.loading = false;
      }
    });
  }

  retry(): void {
    let failedIndex = -1;
    for (let index = this.messages.length - 1; index >= 0; index -= 1) {
      if (this.messages[index].failed) {
        failedIndex = index;
        break;
      }
    }
    if (failedIndex >= 0) {
      this.messages = this.messages.slice(0, failedIndex);
    }
    this.errorMessage = '';
    this.requestAnswer(this.lastQuestion);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.submit();
    }
  }

  focusQuestion(): void {
    this.questionInput?.nativeElement.focus();
  }
}

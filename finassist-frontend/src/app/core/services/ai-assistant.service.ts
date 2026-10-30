import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AskResponse } from '../models/transaction.models';

@Injectable({ providedIn: 'root' })
export class AiAssistantService {
  private readonly endpoint = `${environment.apiBaseUrl}/ask`;

  constructor(private readonly http: HttpClient) {}

  ask(question: string): Observable<AskResponse> {
    return this.http.post<AskResponse>(this.endpoint, { question });
  }
}

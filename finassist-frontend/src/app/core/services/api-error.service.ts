import { Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class ApiErrorService {
  message(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return 'Cannot reach the FinAssist API. Check that the backend is running on port 8080.';
      }

      const body: unknown = error.error;
      if (typeof body === 'object' && body !== null && 'message' in body) {
        const message = (body as { message?: unknown }).message;
        if (typeof message === 'string' && message.trim()) {
          return message;
        }
      }

      if (error.status === 503) {
        return 'The AI service is unavailable. Check that Ollama is running, then try again.';
      }

      return fallback;
    }

    return fallback;
  }
}

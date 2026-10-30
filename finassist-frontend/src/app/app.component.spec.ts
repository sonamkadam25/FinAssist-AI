import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AppComponent } from './app.component';
import { routes } from './app.routes';

describe('Application navigation', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
  });

  it('renders the FinAssist navigation and routes to the assistant without a page reload', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/assistant');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(router.url).toBe('/assistant');
    expect(fixture.nativeElement.textContent).toContain('FinAssist');
    expect(fixture.nativeElement.textContent).toContain('Ask your assistant');
  });
});

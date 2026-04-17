import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, inject, PLATFORM_ID } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { isPlatformBrowser } from '@angular/common';
import { AdminComponent } from './admin/admin.component';

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule, AdminComponent],
  standalone: true,
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  mode: 'login' | 'register' = 'login';
  loginMode: 'none' | 'remote-federation' | 'google' = 'none';

  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);

  backendBaseUrl = 'http://localhost:8082';

  loginEmail = '';
  loginPassword = '';
  registerFirstName = '';
  registerLastName = '';
  registerEmail = '';
  registerPassword = '';
  registerConfirmPassword = '';

  token = '';
  refreshToken: string | null = null;
  lastTokenResponse: any = null;
  private readonly popStateHandler = () => this.syncRouteWithAuth();
  users: any[] = [];
  userInfo: any = null;
  authView = {
    effectiveRole: 'user',
    roles: [] as string[],
    canViewUsers: false,
    homeTitle: 'Trang chu nguoi dung',
    homeDescription: 'Tai khoan user thong thuong. Giao dien don gian.'
  };

  private authViewLoaded = false;

  setMode(nextMode: 'login' | 'register'): void {
    this.mode = nextMode;
  }

  isAuthenticated(): boolean {
    return this.loginMode !== 'none';
  }

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    window.addEventListener('popstate', this.popStateHandler);

    this.processAuthCallback();

    const saved = localStorage.getItem('access_token');
    if (saved) {
      this.token = saved;
      this.refreshToken = localStorage.getItem('refresh_token');
      const savedLoginMode = localStorage.getItem('login_mode');
      if (savedLoginMode === 'remote-federation' || savedLoginMode === 'google') {
        this.loginMode = savedLoginMode;
      } else {
        this.loginMode = 'remote-federation';
      }

      this.loadView();
    }
    this.syncRouteWithAuth();
  }

  ngOnDestroy(): void {
    if (isPlatformBrowser(this.platformId)) {
      window.removeEventListener('popstate', this.popStateHandler);
    }
  }

  private syncRouteWithAuth(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const path = window.location.pathname;
    if (!this.isAuthenticated() && path !== '/') {
      this.navigateTo('/', true);
      return;
    }

    if (!this.isAuthenticated()) {
      return;
    }

    if (!this.authViewLoaded) {
      return;
    }

    if (path !== '/admin') {
      this.navigateTo('/admin', true);
    }
  }

  goToHome(): void {
    this.goToAdmin();
  }

  goToAdmin(): void {
    if (!this.isAuthenticated()) {
      return;
    }
    this.navigateTo('/admin');
  }

  private navigateTo(path: string, replace = false): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    if (window.location.pathname === path) {
      return;
    }

    if (replace) {
      window.history.replaceState({}, '', path);
      return;
    }
    window.history.pushState({}, '', path);
  }

  private authHeaders(): HttpHeaders {
    return new HttpHeaders().set('Authorization', `Bearer ${this.token}`);
  }

  login(): void {
    this.loginWithBackendRemoteFederation();
  }

  loginGoogle(event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.loginWithGoogle();
  }

  register(): void {
    this.registerToExternalDatabase();
  }

  loadUsers(): void {
    this.http.get<any[]>(this.backendUsersEndpoint(), { headers: this.authHeaders() }).subscribe({
      next: (res) => {
        this.users = Array.isArray(res) ? res : [];
      },
      error: () => {
        this.users = [];
      }
    });
  }

  logout(): void {
    const payload = {
      refreshToken: this.refreshToken,
      loginMode: this.loginMode
    };

    this.http.post(this.backendLogoutEndpoint(), payload, {
      headers: new HttpHeaders({ 'Content-Type': 'application/json' })
    }).subscribe({
      next: () => this.clearSession(),
      error: () => this.clearSession()
    });
  }

  forgotPassword(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const redirectUri = this.googleRedirectUri();
    this.http.get<{ url: string }>(this.backendForgotPasswordUrlEndpoint(), {
      params: { redirectUri }
    }).subscribe({
      next: (res) => {
        const url = (res?.url || '').trim();
        if (!url) {
          alert('Khong tao duoc link quen mat khau.');
          return;
        }
        window.location.href = url;
      },
      error: (err) => {
        alert(`Khong lay duoc link quen mat khau: ${err?.error?.message ?? err?.message ?? err}`);
      }
    });
  }

  private clearSession(): void {
    this.authViewLoaded = false;
    this.token = '';
    this.refreshToken = null;
    this.lastTokenResponse = null;
    this.users = [];
    this.userInfo = null;
    this.authView = {
      effectiveRole: 'user',
      roles: [],
      canViewUsers: false,
      homeTitle: 'Trang chu nguoi dung',
      homeDescription: 'Tai khoan user thong thuong. Giao dien don gian.'
    };
    this.loginMode = 'none';
    this.mode = 'login';
    this.loginPassword = '';

    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem('access_token');
      localStorage.removeItem('refresh_token');
      localStorage.removeItem('login_mode');
    }

    this.navigateTo('/', true);
  }

  private loginWithBackendRemoteFederation(): void {
    if (!this.loginEmail || !this.loginPassword) {
      alert('Vui long nhap email va mat khau.');
      return;
    }

    const payload = {
      username: this.loginEmail.trim(),
      password: this.loginPassword
    };

    this.http.post<any>(this.backendRemoteTokenEndpoint(), payload, {
      headers: new HttpHeaders({ 'Content-Type': 'application/json' })
    }).subscribe({
      next: (res) => this.storeTokenResponse(res, 'remote-federation'),
      error: (err) => alert(`Dang nhap loi: ${err?.error?.error_description ?? err?.error?.message ?? err.message ?? err}`)
    });
  }

  private loginWithGoogle(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const redirectUri = this.googleRedirectUri();
    this.http.get<{ url: string }>(this.backendGoogleAuthUrlEndpoint(), {
      params: { redirectUri }
    }).subscribe({
      next: (res) => {
        const url = (res?.url || '').trim();
        if (!url) {
          alert('Backend khong tra ve URL dang nhap Google hop le.');
          return;
        }
        window.location.href = url;
      },
      error: (err) => {

        alert(`Khong tao duoc URL dang nhap Google: ${err?.error?.message ?? err.message ?? err}`);
      }
    });
  }

  private processAuthCallback(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const oauthError = params.get('error_description') ?? params.get('error');

    if (oauthError) {
      alert(`Dang nhap Google loi: ${oauthError}`);
      this.navigateTo('/', true);
      return;
    }

    if (!code) {
      return;
    }

    const payload = {
      code,
      redirectUri: this.googleRedirectUri()
    };

    this.http.post<any>(this.backendGoogleTokenEndpoint(), payload, {
      headers: new HttpHeaders({ 'Content-Type': 'application/json' })
    }).subscribe({
      next: (res) => {

        this.storeTokenResponse(res, 'google');
      },
      error: (err) => {

        alert(`Khong doi duoc token Google qua BE/Keycloak: ${err?.error?.error_description ?? err?.error?.message ?? err.message ?? err}`);
        this.navigateTo('/', true);
      }
    });
  }

  private registerToExternalDatabase(): void {
    const email = (this.registerEmail || '').trim().toLowerCase();
    const password = this.registerPassword || '';
    const confirmPassword = this.registerConfirmPassword || '';
    const firstName = (this.registerFirstName || '').trim();
    const lastName = (this.registerLastName || '').trim();

    if (!email || !password) {
      alert('Vui long nhap email va mat khau de dang ky.');
      return;
    }

    const payload = {
      email,
      firstName,
      lastName,
      password,
      confirmPassword
    };

    this.http.post<any>(this.backendRemoteRegisterEndpoint(), payload, {
      headers: new HttpHeaders({ 'Content-Type': 'application/json' })
    }).subscribe({
      next: () => {
        this.loginEmail = email;
        this.loginPassword = password;
        this.mode = 'login';
        this.registerFirstName = '';
        this.registerLastName = '';
        this.registerEmail = '';
        this.registerPassword = '';
        this.registerConfirmPassword = '';
        alert('Dang ky thanh cong. Ban co the dang nhap ngay.');
      },
      error: (err) => {
        alert(`Dang ky loi: ${err?.error?.message ?? err?.error?.error_description ?? err?.error?.error ?? err.message ?? err}`);
      }
    });
  }

  private storeTokenResponse(res: any, mode: 'remote-federation' | 'google'): void {
    this.loginMode = mode;
    this.mode = 'login';
    this.lastTokenResponse = res;
    this.token = res.access_token ?? res.accessToken ?? res.token ?? '';
    this.refreshToken = res.refresh_token ?? res.refreshToken ?? null;

    if (typeof localStorage !== 'undefined') {
      if (this.token) {
        localStorage.setItem('access_token', this.token);
      } else {
        localStorage.removeItem('access_token');
      }
      if (this.refreshToken) {
        localStorage.setItem('refresh_token', this.refreshToken);
      } else {
        localStorage.removeItem('refresh_token');
      }
      localStorage.setItem('login_mode', this.loginMode);
    }

    if (!this.token) {
      alert(`Dang nhap thanh cong nhung khong nhan duoc access token. Response: ${JSON.stringify(res)}`);
      return;
    }

    this.loadView();
    this.navigateTo('/admin');
  }

  private loadView(): void {
    this.http.get<any>(this.backendViewEndpoint(), { headers: this.authHeaders() }).subscribe({
      next: (res) => {
        this.authView = {
          effectiveRole: (res?.effectiveRole ?? 'user') === 'admin' ? 'Admin' : 'User',
          roles: Array.isArray(res?.roles) ? res.roles : [],
          canViewUsers: !!res?.canViewUsers,
          homeTitle: res?.homeTitle ?? 'Trang chu nguoi dung',
          homeDescription: res?.homeDescription ?? 'Tai khoan user thong thuong. Giao dien don gian.'
        };
        this.authViewLoaded = true;
        this.loadGetInfo();
        if (this.authView.canViewUsers) {
          this.loadUsers();
        } else {
          this.users = [];
        }
        this.syncRouteWithAuth();
      },
      error: () => {
        this.authView = {
          effectiveRole: 'user',
          roles: [],
          canViewUsers: false,
          homeTitle: 'Trang chu nguoi dung',
          homeDescription: 'Tai khoan user thong thuong. Giao dien don gian.'
        };
        this.users = [];
        this.userInfo = null;
        this.authViewLoaded = true;
        this.syncRouteWithAuth();
      }
    });
  }

  private backendRemoteTokenEndpoint(): string {
    return `${this.backendBaseUrl}/api/v1/auth/token/remote-federation`;
  }

  private backendRemoteRegisterEndpoint(): string {
    return `${this.backendBaseUrl}/api/v1/auth/register/remote-federation`;
  }

  private backendGoogleTokenEndpoint(): string {
    return `${this.backendBaseUrl}/api/v1/auth/token/google`;
  }

  private backendGoogleAuthUrlEndpoint(): string {
    return `${this.backendBaseUrl}/api/v1/auth/google/auth-url`;
  }

  private googleRedirectUri(): string {
    if (!isPlatformBrowser(this.platformId)) {
      return 'http://localhost:4200/';
    }
    return `${window.location.origin}/`;
  }

  private backendLogoutEndpoint(): string {
    return `${this.backendBaseUrl}/api/v1/auth/logout`;
  }

  private backendUsersEndpoint(): string {
    return `${this.backendBaseUrl}/api/v1/auth/me`;
  }

  private backendForgotPasswordUrlEndpoint(): string {
    return `${this.backendBaseUrl}/api/v1/auth/forgot-password/url`;
  }

  private backendViewEndpoint(): string {
    return `${this.backendBaseUrl}/api/v1/auth/view`;
  }

  private loadGetInfo(): void {
    if (!this.token) {
      this.userInfo = null;
      return;
    }

    this.http.get<any>(this.backendGetInfoEndpoint(), { headers: this.authHeaders() }).subscribe({
      next: (res) => {
        this.userInfo = res ?? null;
      },
      error: () => {
        this.userInfo = null;
      }
    });
  }

  private backendGetInfoEndpoint(): string {
    return `${this.backendBaseUrl}/api/v1/auth/getinfo`;
  }

}

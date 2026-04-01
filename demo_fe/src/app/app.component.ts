import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, inject, PLATFORM_ID } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { isPlatformBrowser } from '@angular/common';

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  standalone: true,
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  mode: 'login' | 'register' = 'login';
  loginMode: 'none' | 'keycloak-local' | 'google' = 'none';
  title: any;

  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);

  keycloakBaseUrl = 'http://localhost:8080';
  keycloakRealm = 'master';
  keycloakBrowserClientId = 'demo-fe';
  keycloakPasswordClientId = 'demo-fe';
  private readonly oauthStateKey = 'oauth_state';
  private readonly oauthProviderKey = 'oauth_provider';

  loginEmail = '';
  loginPassword = '';
  registerFirstName = '';
  registerLastName = '';
  registerEmail = '';
  registerPassword = '';
  registerConfirmPassword = '';

  token = '';
  refreshToken: string | null = null;
  accessTokenExpiresInSeconds: number | null = null;
  lastTokenResponse: any = null;
  decodedJwt: any = null;
  decodedRefreshJwt: any = null;
  nowEpochSeconds = Math.floor(Date.now() / 1000);
  private timerId: ReturnType<typeof setInterval> | null = null;
  private readonly popStateHandler = () => this.syncRouteWithAuth();
  me: any = null;

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

    this.timerId = setInterval(() => {
      this.nowEpochSeconds = Math.floor(Date.now() / 1000);
    }, 1000);

    const saved = localStorage.getItem('access_token');
    if (saved) {
      this.token = saved;
      this.refreshToken = localStorage.getItem('refresh_token');
      const savedExpiresIn = localStorage.getItem('access_token_expires_in');
      this.accessTokenExpiresInSeconds = savedExpiresIn ? Number(savedExpiresIn) : null;
      this.decodedJwt = this.safeDecodeJwt(this.token);
      this.decodedRefreshJwt = this.safeDecodeJwt(this.refreshToken ?? '');
      const savedLoginMode = localStorage.getItem('login_mode');
      if (savedLoginMode === 'keycloak-local' || savedLoginMode === 'google') {
        this.loginMode = savedLoginMode;
      } else {
        this.loginMode = this.decodedJwt ? 'keycloak-local' : 'none';
      }

      if (this.loginMode !== 'none') {
        this.loadMe();
      }
    }

    this.processAuthCallback();
    this.syncRouteWithAuth();
  }

  ngOnDestroy(): void {
    if (this.timerId) {
      clearInterval(this.timerId);
      this.timerId = null;
    }

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
    }
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
    this.loginWithKeycloakPassword();
  }

  loginGoogle(event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.loginWithGoogle();
  }

  register(): void {
    this.registerToExternalDatabase();
  }

  loadMe(): void {
    this.http.get(this.userInfoEndpoint(), { headers: this.authHeaders() }).subscribe({
      next: (res) => (this.me = res),
      error: () => {
        this.me = this.decodedJwt;
      }
    });
  }

  logout(): void {
    const refreshToken = this.refreshToken;
    const body = new URLSearchParams();
    body.set('client_id', this.loginMode === 'google' ? this.keycloakBrowserClientId : this.keycloakPasswordClientId);
    if (refreshToken) {
      body.set('refresh_token', refreshToken);
    }

    this.http.post(this.logoutEndpoint(), body.toString(), {
      headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' })
    }).subscribe({
      next: () => this.clearSession(),
      error: () => this.clearSession()
    });
  }

  private clearSession(): void {
    this.token = '';
    this.refreshToken = null;
    this.accessTokenExpiresInSeconds = null;
    this.lastTokenResponse = null;
    this.decodedJwt = null;
    this.decodedRefreshJwt = null;
    this.me = null;
    this.loginMode = 'none';
    this.mode = 'login';
    this.loginPassword = '';

    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem('access_token');
      localStorage.removeItem('refresh_token');
      localStorage.removeItem('access_token_expires_in');
      localStorage.removeItem('login_mode');
    }

    this.navigateTo('/', true);
  }

  private loginWithKeycloakPassword(): void {
    if (!this.loginEmail || !this.loginPassword) {
      alert('Vui long nhap email va mat khau.');
      return;
    }

    const body = new URLSearchParams();
    body.set('client_id', this.keycloakPasswordClientId);
    body.set('grant_type', 'password');
    body.set('username', this.loginEmail);
    body.set('password', this.loginPassword);
    body.set('scope', 'openid profile email offline_access');

    this.http.post<any>(this.tokenEndpoint(), body.toString(), {
      headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' })
    }).subscribe({
      next: (res) => this.storeTokenResponse(res, 'keycloak-local'),
      error: (err) => alert(`Dang nhap loi: ${err?.error?.error_description ?? err?.error?.message ?? err.message ?? err}`)
    });
  }

  private loginWithGoogle(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    const state = this.randomState();
    localStorage.setItem(this.oauthStateKey, state);
    localStorage.setItem(this.oauthProviderKey, 'google');

    const params = new URLSearchParams();
    params.set('client_id', this.keycloakBrowserClientId);
    params.set('redirect_uri', this.redirectUri());
    params.set('response_type', 'code');
    params.set('scope', 'openid profile email offline_access');
    params.set('prompt', 'select_account');
    params.set('kc_idp_hint', 'google');
    params.set('state', state);

    window.location.href = `${this.authEndpoint()}?${params.toString()}`;
  }

  private processAuthCallback(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const state = params.get('state');
    const oauthError = params.get('error_description') ?? params.get('error');

    if (oauthError) {
      alert(`Dang nhap Google loi: ${oauthError}`);
      this.navigateTo('/', true);
      return;
    }

    if (!code || !state) {
      return;
    }

    const expectedState = localStorage.getItem(this.oauthStateKey);
    const provider = localStorage.getItem(this.oauthProviderKey) as 'google' | null;
    localStorage.removeItem(this.oauthStateKey);
    localStorage.removeItem(this.oauthProviderKey);

    if (!expectedState || expectedState !== state) {
      alert('Trang thai xac thuc khong hop le. Vui long thu lai.');
      this.navigateTo('/', true);
      return;
    }

    const body = new URLSearchParams();
    body.set('grant_type', 'authorization_code');
    body.set('client_id', this.keycloakBrowserClientId);
    body.set('code', code);
    body.set('redirect_uri', this.redirectUri());
    body.set('scope', 'openid profile email offline_access');

    this.http.post<any>(this.tokenEndpoint(), body.toString(), {
      headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' })
    }).subscribe({
      next: (res) => this.storeTokenResponse(res, provider === 'google' ? 'google' : 'keycloak-local'),
      error: (err) => {
        alert(`Khong doi duoc token tu Keycloak: ${err?.error?.error_description ?? err?.error?.message ?? err.message ?? err}`);
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

    this.http.post<any>(this.customRegisterEndpoint(), payload, {
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

  private storeTokenResponse(res: any, mode: 'keycloak-local' | 'google'): void {
    this.loginMode = mode;
    this.mode = 'login';
    this.lastTokenResponse = res;
    this.token = res.access_token ?? res.accessToken ?? res.token ?? '';
    this.refreshToken = res.refresh_token ?? res.refreshToken ?? null;
    this.accessTokenExpiresInSeconds = Number(res.expires_in ?? res.expiresIn ?? 0) || null;
    this.decodedJwt = this.safeDecodeJwt(this.token);
    this.decodedRefreshJwt = this.safeDecodeJwt(this.refreshToken ?? '');

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
      if (this.accessTokenExpiresInSeconds) {
        localStorage.setItem('access_token_expires_in', String(this.accessTokenExpiresInSeconds));
      } else {
        localStorage.removeItem('access_token_expires_in');
      }
      localStorage.setItem('login_mode', this.loginMode);
    }

    if (!this.token) {
      alert(`Dang nhap thanh cong nhung khong nhan duoc access token. Response: ${JSON.stringify(res)}`);
      return;
    }

    this.loadMe();
    this.navigateTo('/home');
  }

  private tokenEndpoint(): string {
    return `${this.keycloakBaseUrl}/realms/${this.keycloakRealm}/protocol/openid-connect/token`;
  }

  private authEndpoint(): string {
    return `${this.keycloakBaseUrl}/realms/${this.keycloakRealm}/protocol/openid-connect/auth`;
  }

  private registrationEndpoint(): string {
    return `${this.keycloakBaseUrl}/realms/${this.keycloakRealm}/protocol/openid-connect/registrations`;
  }

  private customRegisterEndpoint(): string {
    return `${this.keycloakBaseUrl}/realms/${this.keycloakRealm}/custom-register/register`;
  }

  private userInfoEndpoint(): string {
    return `${this.keycloakBaseUrl}/realms/${this.keycloakRealm}/protocol/openid-connect/userinfo`;
  }

  private logoutEndpoint(): string {
    return `${this.keycloakBaseUrl}/realms/${this.keycloakRealm}/protocol/openid-connect/logout`;
  }

  private redirectUri(): string {
    if (!isPlatformBrowser(this.platformId)) {
      return 'http://localhost:4200/';
    }
    return `${window.location.origin}/`;
  }

  private randomState(): string {
    return Math.random().toString(36).slice(2) + Date.now().toString(36);
  }

  private safeDecodeJwt(token: string): any {
    try {
      if (!token) return null;
      const parts = token.split('.');
      if (parts.length < 2) return null;
      const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = payload + '='.repeat((4 - (payload.length % 4)) % 4);
      const json = decodeURIComponent(escape(atob(padded)));
      return JSON.parse(json);
    } catch {
      return null;
    }
  }

  accessTokenIssuedAt(): string {
    return this.formatEpoch(this.getEpochClaim(this.decodedJwt, 'iat'));
  }

  accessTokenExpiresAt(): string {
    return this.formatEpoch(this.getEpochClaim(this.decodedJwt, 'exp'));
  }

  accessTokenLifetime(): string {
    const fromJwt = this.getLifetime(this.decodedJwt);
    if (fromJwt != null) {
      return this.formatDuration(fromJwt);
    }
    return this.formatDuration(this.accessTokenExpiresInSeconds);
  }

  accessTokenRemaining(): string {
    return this.formatRemaining(this.getRemaining(this.decodedJwt));
  }

  refreshTokenIssuedAt(): string {
    return this.formatEpoch(this.getEpochClaim(this.decodedRefreshJwt, 'iat'));
  }

  refreshTokenExpiresAt(): string {
    return this.formatEpoch(this.getEpochClaim(this.decodedRefreshJwt, 'exp'));
  }

  refreshTokenLifetime(): string {
    return this.formatDuration(this.getLifetime(this.decodedRefreshJwt));
  }

  refreshTokenRemaining(): string {
    return this.formatRemaining(this.getRemaining(this.decodedRefreshJwt));
  }

  private getEpochClaim(payload: any, key: 'iat' | 'exp'): number | null {
    if (!payload || typeof payload[key] !== 'number') {
      return null;
    }
    return payload[key];
  }

  private getLifetime(payload: any): number | null {
    const iat = this.getEpochClaim(payload, 'iat');
    const exp = this.getEpochClaim(payload, 'exp');
    if (iat == null || exp == null || exp < iat) {
      return null;
    }
    return exp - iat;
  }

  private getRemaining(payload: any): number | null {
    const exp = this.getEpochClaim(payload, 'exp');
    if (exp == null) {
      return null;
    }
    return exp - this.nowEpochSeconds;
  }

  private formatEpoch(epochSeconds: number | null): string {
    if (epochSeconds == null || epochSeconds <= 0) {
      return 'Khong xac dinh';
    }
    return new Date(epochSeconds * 1000).toLocaleString('vi-VN');
  }

  private formatDuration(totalSeconds: number | null): string {
    if (totalSeconds == null || totalSeconds < 0) {
      return 'Khong xac dinh';
    }
    return this.toDurationString(totalSeconds);
  }

  private formatRemaining(remainingSeconds: number | null): string {
    if (remainingSeconds == null) {
      return 'Khong xac dinh';
    }
    if (remainingSeconds <= 0) {
      return 'Da het han';
    }
    return this.toDurationString(remainingSeconds);
  }

  private toDurationString(totalSeconds: number): string {
    const days = Math.floor(totalSeconds / 86400);
    const hours = Math.floor((totalSeconds % 86400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = Math.floor(totalSeconds % 60);

    const parts: string[] = [];
    if (days > 0) parts.push(`${days} ngay`);
    if (hours > 0) parts.push(`${hours} gio`);
    if (minutes > 0) parts.push(`${minutes} phut`);
    parts.push(`${seconds} giay`);
    return parts.join(' ');
  }
}

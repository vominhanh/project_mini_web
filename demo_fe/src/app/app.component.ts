import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  standalone: true,
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  mode: 'login' | 'register' = 'login';
  loginProvider: 'provider-db' | 'remote-federation' = 'provider-db';
  loginMode: 'none' | 'provider-db' | 'remote-federation' = 'none';
  title: any;

  private readonly http = inject(HttpClient);

  apiBase = 'http://localhost:8081';


  loginEmail = '';
  loginPassword = '';
  
  token = '';
  me: any = null;
  users: Array<{ id: number; email: string; name: string; createdAt?: string }> = [];

  formEmail = '';
  formName = '';
  editingId: number | null = null;

  setMode(nextMode: 'login' | 'register'): void {
    this.mode = nextMode;
  }

  setLoginProvider(provider: 'provider-db' | 'remote-federation'): void {
    this.loginProvider = provider;
  }

  registerName = '';
  registerEmail = '';
  registerPassword = '';
  registerConfirmPassword = '';

  ngOnInit(): void {
    if (typeof localStorage === 'undefined') return;
    const saved = localStorage.getItem('access_token');
    if (saved) {
      this.token = saved;
      this.loadMe();
      this.loadUsers();
    }
  }

  private authHeaders(): HttpHeaders {
    return new HttpHeaders().set('Authorization', `Bearer ${this.token}`);
  }

  login(): void {
    const endpoint = this.loginProvider === 'provider-db'
      ? `${this.apiBase}/api/v1/auth/token/provider-db`
      : `${this.apiBase}/api/v1/auth/token/remote-federation`;

    this.http.post<any>(endpoint, {
      username: this.loginEmail,
      password: this.loginPassword
    }).subscribe({
      next: (res) => {
        this.loginMode = this.loginProvider;
        if (this.loginProvider === 'remote-federation') {
          this.token = res.accessToken ?? res.access_token ?? res.token ?? '';
          if (typeof localStorage !== 'undefined') {
            localStorage.setItem('access_token', this.token);
          }
          this.loadMe();
          this.loadUsers();
        } else {
          this.token = '';
          this.me = { mode: 'provider-db', username: this.loginEmail, message: 'Backend tu xac thuc local DB thanh cong' };
          this.users = [];
        }
      },
      error: (err) => {
        alert(`Dang nhap loi: ${err?.error?.message ?? err.message ?? err}`);
      }
    });
  }

  register(): void {
    if (!this.registerEmail || !this.registerName || !this.registerPassword) {
      alert('Vui lòng nhập đầy đủ thông tin đăng ký');
      return;
    }
    if (this.registerPassword !== this.registerConfirmPassword) {
      alert('Mật khẩu xác nhận không khớp');
      return;
    }

    this.http.post<any>(`${this.apiBase}/api/v1/auth/register/provider-db`, {
      email: this.registerEmail,
      name: this.registerName,
      password: this.registerPassword
    }).subscribe({
      next: () => {
        alert('Dang ky thanh cong vao Provider DB source. Vui long dang nhap bang cong Provider DB.');
        this.mode = 'login';
        this.loginEmail = this.registerEmail;
        this.loginPassword = '';
        this.registerName = '';
        this.registerEmail = '';
        this.registerPassword = '';
        this.registerConfirmPassword = '';
      },
      error: (err) => {
        alert(`Dang ky loi: ${err?.error?.message ?? err.message ?? err}`);
      }
    });
  }

  loadMe(): void {
    this.http.get(`${this.apiBase}/api/v1/auth/me`, { headers: this.authHeaders() }).subscribe({
      next: (res) => (this.me = res),
      error: () => {}
    });
  }

  loadUsers(): void {
    this.http.get<Array<{ id: number; email: string; name: string; createdAt: string }>>(
      `${this.apiBase}/api/v1/users`,
      { headers: this.authHeaders() }
    ).subscribe({
      next: (res) => (this.users = res),
      error: (err) => {
        if (err?.status === 401) {
          this.token = '';
          this.me = null;
          this.users = [];
          if (typeof localStorage !== 'undefined') {
            localStorage.removeItem('access_token');
          }
          return;
        }
        alert(`Lay users loi: ${err?.error?.message ?? err.message ?? err}`);
      }
    });
  }

  startCreate(): void {
    this.editingId = null;
    this.formEmail = '';
    this.formName = '';
  }

  startEdit(u: { id: number; email: string; name: string }): void {
    this.editingId = u.id;
    this.formEmail = u.email;
    this.formName = u.name;
  }

  saveUser(): void {
    const payload = {
      email: this.formEmail,
      name: this.formName
    };

    if (this.editingId == null) {
      this.http.post<any>(`${this.apiBase}/api/v1/users`, payload, { headers: this.authHeaders() }).subscribe({
        next: () => {
          this.startCreate();
          this.loadUsers();
        },
        error: (err) => alert(`Tao user loi: ${err?.error?.message ?? err.message ?? err}`)
      });
    } else {
      this.http.put<any>(`${this.apiBase}/api/v1/users/${this.editingId}`, payload, { headers: this.authHeaders() }).subscribe({
        next: () => {
          this.startCreate();
          this.loadUsers();
        },
        error: (err) => alert(`Cap nhat user loi: ${err?.error?.message ?? err.message ?? err}`)
      });
    }
  }

  deleteUser(id: number): void {
    if (!confirm('Xoa user?')) return;
    this.http.delete(`${this.apiBase}/api/v1/users/${id}`, { headers: this.authHeaders() }).subscribe({
      next: () => this.loadUsers(),
      error: (err) => alert(`Xoa user loi: ${err?.error?.message ?? err.message ?? err}`)
    });
  }
}

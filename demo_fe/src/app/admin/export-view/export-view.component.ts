import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, Input, inject } from '@angular/core';

type ExportColumnKey = 'id' | 'firstname' | 'lastname' | 'email' | 'username' | 'role';

@Component({
  selector: 'app-admin-export-view',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './export-view.component.html',
  styleUrl: './export-view.component.css'
})
export class ExportViewComponent {
  private readonly http = inject(HttpClient);
  private readonly defaultExportColumns: ExportColumnKey[] = ['id', 'firstname', 'lastname', 'email', 'username', 'role'];

  @Input() authView: any;
  @Input() users: any[] = [];
  @Input() token = '';
  @Input() backendBaseUrl = 'http://localhost:8082';

  downloading: 'pdf' | 'xlsx' | null = null;
  exportColumns: { key: ExportColumnKey; label: string }[] = [
    { key: 'id', label: 'ID' },
    { key: 'firstname', label: 'First name' },
    { key: 'lastname', label: 'Last name' },
    { key: 'email', label: 'Email' },
    { key: 'username', label: 'Username' },
    { key: 'role', label: 'Role' }
  ];
  selectedExportColumns = new Set<ExportColumnKey>(this.defaultExportColumns);

  private authHeaders(): HttpHeaders {
    return new HttpHeaders().set('Authorization', `Bearer ${this.token}`);
  }

  private selectedColumnsForRequest(): ExportColumnKey[] {
    const chosen = this.exportColumns
      .map((c) => c.key)
      .filter((key) => this.selectedExportColumns.has(key));
    return chosen.length > 0 ? chosen : [...this.defaultExportColumns];
  }

  private buildExportUrl(format: 'pdf' | 'xlsx'): string {
    const columns = this.selectedColumnsForRequest();
    const params = new URLSearchParams();
    params.append('format', format);
    for (const col of columns) {
      params.append('columns', col);
    }
    return `${this.backendBaseUrl}/api/reports/invoice/export?${params.toString()}`;
  }

  private resetExportColumnsToDefault(): void {
    this.selectedExportColumns = new Set<ExportColumnKey>(this.defaultExportColumns);
  }

  toggleExportColumn(key: ExportColumnKey): void {
    if (this.selectedExportColumns.has(key)) {
      if (this.selectedExportColumns.size === 1) return;
      this.selectedExportColumns.delete(key);
      this.selectedExportColumns = new Set(this.selectedExportColumns);
      return;
    }
    this.selectedExportColumns.add(key);
    this.selectedExportColumns = new Set(this.selectedExportColumns);
  }

  private saveBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  private filenameFromContentDisposition(header: string | null, fallback: string): string {
    if (!header) return fallback;
    const m = /filename\*?=(?:UTF-8'')?["']?([^"';]+)/i.exec(header);
    if (m?.[1]) {
      try { return decodeURIComponent(m[1].trim()); } catch { return m[1].trim(); }
    }
    return fallback;
  }

  download(format: 'pdf' | 'xlsx'): void {
    if (!this.token || this.downloading) return;
    this.downloading = format;
    const fallback = `facepay-auth-log-${new Date().toISOString().slice(0, 10)}.${format}`;

    this.http.get(this.buildExportUrl(format), {
      headers: this.authHeaders(),
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (res) => {
        const body = res.body;
        if (!body) {
          alert(`Khong nhan duoc file ${format.toUpperCase()}.`);
          return;
        }
        const name = this.filenameFromContentDisposition(res.headers.get('Content-Disposition'), fallback);
        this.saveBlob(body, name);
      },
      error: (err) => {
        this.downloading = null;
        const msg = err?.status === 403 ? 'Ban khong co quyen admin.' : err?.message ?? `Loi tai ${format.toUpperCase()}`;
        alert(`Tai ${format.toUpperCase()} loi: ${msg}`);
      },
      complete: () => {
        this.downloading = null;
        this.resetExportColumnsToDefault();
      }
    });
  }
}

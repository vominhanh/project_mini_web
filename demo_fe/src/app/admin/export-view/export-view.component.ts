import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, Input, inject } from '@angular/core';

type ExportColumnKey = 'id' | 'firstname' | 'lastname' | 'email' | 'username' | 'role';
type TemplateColumnKey = 'id' | 'name' | 'price' | 'capacity' | 'city';

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
  private readonly defaultTemplateColumns: TemplateColumnKey[] = ['id', 'name', 'price', 'capacity', 'city'];

  @Input() authView: any;
  @Input() users: any[] = [];
  @Input() token = '';
  @Input() backendBaseUrl = 'http://localhost:8082';

  downloading: 'pdf' | 'xlsx' | null = null;
  previewLoading = false;
  showPreviewModal = false;
  pendingFormat: 'pdf' | 'xlsx' | null = null;
  previewData: {
    userColumns: string[];
    roomColumns: string[];
    userRows: Record<string, any>[];
    roomRows: Record<string, any>[];
  } = { userColumns: [], roomColumns: [], userRows: [], roomRows: [] };

  exportColumns: { key: ExportColumnKey; label: string }[] = [
    { key: 'id', label: 'ID' },
    { key: 'firstname', label: 'First name' },
    { key: 'lastname', label: 'Last name' },
    { key: 'email', label: 'Email' },
    { key: 'username', label: 'Username' },
    { key: 'role', label: 'Role' }
  ];
  templateColumns: { key: TemplateColumnKey; label: string }[] = [
    { key: 'id', label: 'Room ID' },
    { key: 'name', label: 'Tên phòng' },
    { key: 'price', label: 'Giá' },
    { key: 'capacity', label: 'Sức chứa' },
    { key: 'city', label: 'Thành phố' }
  ];
  selectedExportColumns = new Set<ExportColumnKey>(this.defaultExportColumns);
  selectedTemplateColumns = new Set<TemplateColumnKey>(this.defaultTemplateColumns);

  private authHeaders(): HttpHeaders {
    return new HttpHeaders().set('Authorization', `Bearer ${this.token}`);
  }

  private selectedUserColumnsForRequest(): ExportColumnKey[] {
    const chosen = this.exportColumns
      .map((c) => c.key)
      .filter((key) => this.selectedExportColumns.has(key));
    return chosen.length > 0 ? chosen : [...this.defaultExportColumns];
  }

  private selectedTemplateColumnsForRequest(): TemplateColumnKey[] {
    const chosen = this.templateColumns
      .map((c) => c.key)
      .filter((key) => this.selectedTemplateColumns.has(key));
    return chosen.length > 0 ? chosen : [...this.defaultTemplateColumns];
  }

  private selectedColumnsForRequest(): string[] {
    const userCols = this.selectedUserColumnsForRequest().map((key) => `user:${key}`);
    const roomCols = this.selectedTemplateColumnsForRequest().map((key) => `room:${key}`);
    return [...userCols, ...roomCols];
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
    this.selectedTemplateColumns = new Set<TemplateColumnKey>(this.defaultTemplateColumns);
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

  toggleTemplateColumn(key: TemplateColumnKey): void {
    if (this.selectedTemplateColumns.has(key)) {
      if (this.selectedTemplateColumns.size === 1) return;
      this.selectedTemplateColumns.delete(key);
      this.selectedTemplateColumns = new Set(this.selectedTemplateColumns);
      return;
    }
    this.selectedTemplateColumns.add(key);
    this.selectedTemplateColumns = new Set(this.selectedTemplateColumns);
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

  private buildPreviewUrl(): string {
    const columns = this.selectedColumnsForRequest();
    const params = new URLSearchParams();
    for (const col of columns) {
      params.append('columns', col);
    }
    return `${this.backendBaseUrl}/api/reports/invoice/preview?${params.toString()}`;
  }

  download(format: 'pdf' | 'xlsx'): void {
    if (!this.token || this.downloading || this.previewLoading) return;
    this.previewLoading = true;
    this.pendingFormat = format;
    this.http.get<any>(this.buildPreviewUrl(), {
      headers: this.authHeaders()
    }).subscribe({
      next: (res) => {
        this.previewData = {
          userColumns: Array.isArray(res?.userColumns) ? res.userColumns : [],
          roomColumns: Array.isArray(res?.roomColumns) ? res.roomColumns : [],
          userRows: Array.isArray(res?.userRows) ? res.userRows : [],
          roomRows: Array.isArray(res?.roomRows) ? res.roomRows : []
        };
        this.showPreviewModal = true;
      },
      error: (err) => {
        const msg = err?.status === 403 ? 'Ban khong co quyen admin.' : err?.message ?? 'Loi xem truoc du lieu';
        alert(`Lay du lieu xem truoc that bai: ${msg}`);
      },
      complete: () => {
        this.previewLoading = false;
      }
    });
  }

  confirmExport(): void {
    const format = this.pendingFormat;
    if (!format || !this.token || this.downloading) {
      return;
    }
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
        this.pendingFormat = null;
        this.showPreviewModal = false;
        this.resetExportColumnsToDefault();
      }
    });
  }

  cancelPreview(): void {
    this.showPreviewModal = false;
    this.pendingFormat = null;
  }
}

import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';

type ExportColumnKey = 'id' | 'firstname' | 'lastname' | 'email' | 'username' | 'role';

@Component({
  selector: 'app-export',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './export.component.html',
  styleUrl: './export.component.css'
})
export class ExportComponent implements OnChanges {
  private readonly http = inject(HttpClient);
  private readonly defaultExportColumns: ExportColumnKey[] = ['id', 'firstname', 'lastname', 'email', 'username', 'role'];

  @Input() authView: {
    effectiveRole: string;
    roles: string[];
    canViewUsers: boolean;
    homeTitle: string;
    homeDescription: string;
  } = {
      effectiveRole: 'user',
      roles: [],
      canViewUsers: false,
      homeTitle: 'Trang chu nguoi dung',
      homeDescription: 'Tai khoan user thong thuong. Giao dien don gian.'
    };

  @Input() userInfo: any = null;
  @Input() users: any[] = [];
  @Input() token = '';
  @Input() refreshToken: string | null = null;
  @Input() backendBaseUrl = 'http://localhost:8082';

  @Output() backClicked = new EventEmitter<void>();
  downloading: 'pdf' | 'xlsx' | null = null;
  uploadingConfig = false;
  showReportConfigPanel = false;
  selectedJrxmlFile: File | null = null;
  selectedLogoFile: File | null = null;
  reportConfig: {
    jrxmlFileName: string;
    logoFileName: string;
    updatedAt: string;
  } | null = null;
  configError = '';
  exportColumns: { key: ExportColumnKey; label: string }[] = [
    { key: 'id', label: 'ID' },
    { key: 'firstname', label: 'First name' },
    { key: 'lastname', label: 'Last name' },
    { key: 'email', label: 'Email' },
    { key: 'username', label: 'Username' },
    { key: 'role', label: 'Role' }
  ];
  selectedExportColumns = new Set<ExportColumnKey>(this.defaultExportColumns);

  goBack(): void {
    this.backClicked.emit();
  }

  toggleReportConfigPanel(): void {
    this.showReportConfigPanel = !this.showReportConfigPanel;
    if (this.showReportConfigPanel && this.token && !this.reportConfig) {
      this.loadReportConfig();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['token'] && this.token) {
      this.loadReportConfig();
    }
  }

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
      if (this.selectedExportColumns.size === 1) {
        return;
      }
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
    if (!header) {
      return fallback;
    }
    const m = /filename\*?=(?:UTF-8'')?["']?([^"';]+)/i.exec(header);
    if (m?.[1]) {
      try {
        return decodeURIComponent(m[1].trim());
      } catch {
        return m[1].trim();
      }
    }
    return fallback;
  }

  download(format: 'pdf' | 'xlsx'): void {
    if (!this.token || this.downloading) {
      return;
    }

    this.downloading = format;

    const fallback = `facepay-auth-log-${new Date().toISOString().slice(0, 10)}.${format}`;

    this.http
      .get(this.buildExportUrl(format), {
        headers: this.authHeaders(),
        responseType: 'blob',
        observe: 'response'
      })
      .subscribe({
        next: (res) => {
          const body = res.body;
          if (!body) {
            alert(`Khong nhan duoc file ${format.toUpperCase()}.`);
            return;
          }

          const name = this.filenameFromContentDisposition(
            res.headers.get('Content-Disposition'),
            fallback
          );

          this.saveBlob(body, name);
        },
        error: (err) => {
          this.downloading = null;

          const msg =
            err?.status === 403
              ? 'Ban khong co quyen admin.'
              : err?.message ?? `Loi tai ${format.toUpperCase()}`;

          alert(`Tai ${format.toUpperCase()} loi: ${msg}`);
        },
        complete: () => {
          this.downloading = null;
          this.resetExportColumnsToDefault();
        }
      });
  }

  onJrxmlSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedJrxmlFile = file;
  }

  onLogoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedLogoFile = file;
  }

  uploadTemplateConfig(): void {
    if (!this.token || this.uploadingConfig) {
      return;
    }
    if (!this.selectedJrxmlFile && !this.selectedLogoFile) {
      alert('Vui long chon it nhat 1 file (jrxml hoac logo) de cap nhat.');
      return;
    }
    const confirmed = window.confirm('Ban co chac chan muon cap nhat cau hinh file report khong?');
    if (!confirmed) {
      return;
    }

    this.uploadingConfig = true;
    this.configError = '';
    const formData = new FormData();
    if (this.selectedJrxmlFile) {
      formData.append('jrxmlFile', this.selectedJrxmlFile, this.selectedJrxmlFile.name);
    }
    if (this.selectedLogoFile) {
      formData.append('logoFile', this.selectedLogoFile, this.selectedLogoFile.name);
    }
    this.http
      .post<any>(`${this.backendBaseUrl}/api/reports/invoice/config`, formData, {
        headers: this.authHeaders()
      })
      .subscribe({
        next: (res) => {
          this.reportConfig = {
            jrxmlFileName: res?.jrxmlFileName ?? '-',
            logoFileName: res?.logoFileName ?? '-',
            updatedAt: res?.updatedAt ?? ''
          };
          this.selectedJrxmlFile = null;
          this.selectedLogoFile = null;
          alert('Cap nhat cau hinh report thanh cong.');
        },
        error: (err) => {
          const msg = err?.error?.message ?? err?.error?.error ?? err?.message ?? 'Khong cap nhat duoc report';
          this.configError = msg;
          alert(`Cap nhat that bai: ${msg}`);
        },
        complete: () => {
          this.uploadingConfig = false;
        }
      });
  }

  loadReportConfig(): void {
    if (!this.token) {
      this.reportConfig = null;
      return;
    }
    this.http
      .get<any>(`${this.backendBaseUrl}/api/reports/invoice/config`, {
        headers: this.authHeaders()
      })
      .subscribe({
        next: (res) => {
          this.reportConfig = {
            jrxmlFileName: res?.jrxmlFileName ?? '-',
            logoFileName: res?.logoFileName ?? '-',
            updatedAt: res?.updatedAt ?? ''
          };
          this.configError = '';
        },
        error: (err) => {
          this.reportConfig = null;
          this.configError = err?.error?.message ?? err?.message ?? 'Khong tai duoc cau hinh report hien tai.';
        }
      });
  }
}

import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-export',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './export.component.html',
  styleUrl: './export.component.css'
})
export class ExportComponent {
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

  @Output() backClicked = new EventEmitter<void>();

  goBack(): void {
    this.backClicked.emit();
  }
}

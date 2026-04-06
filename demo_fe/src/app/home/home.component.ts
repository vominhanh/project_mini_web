import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent {
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

  @Input() token = '';
  @Input() refreshToken: string | null = null;
  @Input() userInfo: any = null;
  @Input() users: any[] = [];

  @Output() logoutClicked = new EventEmitter<void>();
  @Output() exportClicked = new EventEmitter<void>();

  logout(): void {
    this.logoutClicked.emit();
  }

  goExport(): void {
    this.exportClicked.emit();
  }

}

import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { BookingComponent } from './booking/booking.component';
import { CreateRoomComponent } from './create-room/create-room.component';
import { ExportViewComponent } from './export-view/export-view.component';

type AdminPanel = 'booking' | 'create_room' | 'export' | 'settings';

@Component({
    selector: 'app-admin',
    standalone: true,
    imports: [CommonModule, BookingComponent, CreateRoomComponent, ExportViewComponent],
    templateUrl: './admin.component.html',
    styleUrl: './admin.component.css'
})
export class AdminComponent implements OnInit {
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

    activePanel: AdminPanel = 'create_room';
    headerMenuOpen = false;

    ngOnInit(): void {
        if (this.authView.effectiveRole.toLowerCase() === 'admin') {
            this.activePanel = 'booking';
        } else {
            this.activePanel = 'create_room';
        }
    }

    setPanel(panel: AdminPanel): void {
        this.activePanel = panel;
        this.headerMenuOpen = false;
    }

    toggleHeaderMenu(): void {
        this.headerMenuOpen = !this.headerMenuOpen;
    }

    closeHeaderMenu(): void {
        this.headerMenuOpen = false;
    }

    onUpdateProfile(): void {
        this.headerMenuOpen = false;
        alert('Chức năng Cập nhật thông tin đang phát triển.');
    }

    onChangePassword(): void {
        this.headerMenuOpen = false;
        alert('Chức năng Đổi mật khẩu đang phát triển.');
    }

    onLogout(): void {
        this.headerMenuOpen = false;
        this.logoutClicked.emit();
    }
}

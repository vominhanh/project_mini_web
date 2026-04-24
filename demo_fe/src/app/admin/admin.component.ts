import { isPlatformBrowser, CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, PLATFORM_ID, SimpleChanges, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Client } from '@stomp/stompjs';
import { BookingComponent } from './booking/booking.component';
import { CreateRoomComponent } from './create-room/create-room.component';
import { ExportViewComponent } from './export-view/export-view.component';

type AdminPanel = 'booking' | 'create_room' | 'export' | 'settings';

interface WorkflowNotificationEvent {
    id?: number;
    eventType?: string;
    roomId?: number;
    roomName?: string;
    ownerEmail?: string;
    approved?: boolean;
    retryCount?: number;
    status?: string;
    message?: string;
    createdAt?: string;
    targetPanel?: string;
}

@Component({
    selector: 'app-admin',
    standalone: true,
    imports: [CommonModule, BookingComponent, CreateRoomComponent, ExportViewComponent],
    templateUrl: './admin.component.html',
    styleUrl: './admin.component.css'
})
export class AdminComponent implements OnChanges, OnDestroy {
    private readonly platformId = inject(PLATFORM_ID);
    private readonly http = inject(HttpClient);
    private stompClient: Client | null = null;
    private connectedRecipient = '';

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
    @Input() backendBaseUrl = 'http://localhost:8082';

    @Output() logoutClicked = new EventEmitter<void>();

    activePanel: AdminPanel = 'create_room';
    headerMenuOpen = false;
    notificationPanelOpen = false;
    notifications: WorkflowNotificationEvent[] = [];
    unreadNotificationCount = 0;

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['authView']) {
            this.syncDefaultPanel();
        }
        if (changes['token'] || changes['userInfo'] || changes['backendBaseUrl']) {
            this.loadPersistedNotifications();
            this.syncRealtimeNotificationConnection();
        }
    }

    ngOnDestroy(): void {
        this.disconnectRealtimeNotification();
    }

    private syncDefaultPanel(): void {
        if (this.authView.effectiveRole.toLowerCase() === 'admin') {
            this.activePanel = 'booking';
            return;
        }

        if (this.activePanel === 'booking' || this.activePanel === 'export') {
            this.activePanel = 'create_room';
        }
    }

    setPanel(panel: AdminPanel): void {
        this.activePanel = panel;
        this.headerMenuOpen = false;
    }

    toggleHeaderMenu(): void {
        this.notificationPanelOpen = false;
        this.headerMenuOpen = !this.headerMenuOpen;
    }

    toggleNotificationPanel(event: Event): void {
        event.stopPropagation();
        this.headerMenuOpen = false;
        this.notificationPanelOpen = !this.notificationPanelOpen;
        if (this.notificationPanelOpen) {
            this.unreadNotificationCount = 0;
        }
    }

    closeHeaderMenu(): void {
        this.headerMenuOpen = false;
        this.notificationPanelOpen = false;
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
        this.notificationPanelOpen = false;
        this.disconnectRealtimeNotification();
        this.notifications = [];
        this.unreadNotificationCount = 0;
        this.clearNotificationsCache();
        this.logoutClicked.emit();
    }

    openNotification(event: WorkflowNotificationEvent): void {
        if (!event) {
            return;
        }

        const targetPanel = this.resolveNotificationTargetPanel(event);
        if (targetPanel) {
            this.setPanel(targetPanel);
        }
        this.notificationPanelOpen = false;
    }

    notificationTitle(event: WorkflowNotificationEvent): string {
        if (event.eventType === 'USER_TO_ADMIN') {
            return 'Yeu cau tao phong moi';
        }
        if (event.eventType === 'USER_TO_ADMIN_UPDATE') {
            return 'Yeu cau duyet lai phong da cap nhat';
        }
        if (event.eventType === 'APPROVED') {
            return 'Phòng đã được duyệt';
        }
        if (event.eventType === 'REJECTED_NEEDS_UPDATE') {
            return 'Phòng bị từ chối, cần cập nhật';
        }
        if (event.eventType === 'REJECTED_FINAL') {
            return 'Phòng bị từ chối vĩnh viễn';
        }
        return 'Thông báo workflow';
    }

    notificationMeta(event: WorkflowNotificationEvent): string {
        const owner = event.ownerEmail ? `Nguoi gui: ${event.ownerEmail}` : '';
        const room = event.roomName ? `Phòng: ${event.roomName}` : '';
        const retry = Number.isFinite(event.retryCount) ? `Lần sửa: ${event.retryCount}` : '';
        const pieces = [owner, room, retry].filter((v) => !!v);
        return pieces.join(' | ');
    }

    private syncRealtimeNotificationConnection(): void {
        if (!isPlatformBrowser(this.platformId)) {
            return;
        }

        const recipient = this.resolveCurrentUserEmail();
        if (!this.token || !recipient) {
            this.disconnectRealtimeNotification();
            return;
        }

        if (this.stompClient?.connected && this.connectedRecipient === recipient) {
            return;
        }

        this.disconnectRealtimeNotification();

        const websocketUrl = this.toWsBrokerUrl(this.backendBaseUrl) + '/ws';
        const client = new Client({
            brokerURL: websocketUrl,
            reconnectDelay: 5000,
            onConnect: () => {
                this.connectedRecipient = recipient;
                client.subscribe('/topic/rooms', (message) => {
                    try {
                        const event = JSON.parse(message.body) as WorkflowNotificationEvent;
                        this.pushNotificationIfRelevant(event);
                    } catch {
                        // Ignore malformed event payload.
                    }
                });
            }
        });

        client.activate();
        this.stompClient = client;
    }

    private loadPersistedNotifications(): void {
        if (!isPlatformBrowser(this.platformId) || !this.token) {
            return;
        }

        this.http.get<WorkflowNotificationEvent[]>(`${this.toHttpBase(this.backendBaseUrl)}/api/v1/notifications/active`, {
            headers: this.authHeaders()
        }).subscribe({
            next: (items) => {
                const activeNotifications = Array.isArray(items) ? items : [];
                this.notifications = activeNotifications;
                this.unreadNotificationCount = this.notificationPanelOpen ? 0 : this.notifications.length;
                if (activeNotifications.length === 0) {
                    this.clearNotificationsCache();
                }
            },
            error: () => {
                this.notifications = [];
                this.unreadNotificationCount = 0;
            }
        });
    }

    private disconnectRealtimeNotification(): void {
        if (this.stompClient) {
            this.stompClient.deactivate();
            this.stompClient = null;
        }
        this.connectedRecipient = '';
    }

    private pushNotificationIfRelevant(event: WorkflowNotificationEvent): void {
        if (event.eventType === 'USER_TO_ADMIN' || event.eventType === 'USER_TO_ADMIN_UPDATE') {
            if (!this.isAdminView()) {
                return;
            }

            this.removeCompletedNotifications(event);

            this.notifications = [event, ...this.notifications].slice(0, 10);
            if (!this.notificationPanelOpen) {
                this.unreadNotificationCount += 1;
            }
            this.persistNotificationsCache();
            return;
        }

        this.removeCompletedNotifications(event);

        const ownerEmail = this.normalizeEmail(event.ownerEmail);
        const recipient = this.resolveCurrentUserEmail();
        if (!ownerEmail || !recipient || ownerEmail !== recipient) {
            return;
        }

        this.notifications = [event, ...this.notifications].slice(0, 10);
        if (!this.notificationPanelOpen) {
            this.unreadNotificationCount += 1;
        }
        this.persistNotificationsCache();
    }

    private resolveCurrentUserEmail(): string {
        const currentUser = this.userInfo?.currentUser ?? {};
        const candidates = [
            currentUser?.email,
            currentUser?.username,
            this.userInfo?.email,
            this.userInfo?.username
        ];

        for (const candidate of candidates) {
            const normalized = this.normalizeEmail(candidate);
            if (normalized) {
                return normalized;
            }
        }
        return '';
    }

    private normalizeEmail(value: unknown): string {
        if (typeof value !== 'string') {
            return '';
        }
        const normalized = value.trim().toLowerCase();
        return normalized || '';
    }

    private authHeaders(): HttpHeaders {
        return new HttpHeaders().set('Authorization', `Bearer ${this.token}`);
    }

    private toHttpBase(baseUrl: string): string {
        const normalized = (baseUrl || '').trim().replace(/\/$/, '');
        if (!normalized) {
            return 'http://localhost:8082';
        }
        if (normalized.startsWith('ws://')) {
            return normalized.replace('ws://', 'http://');
        }
        if (normalized.startsWith('wss://')) {
            return normalized.replace('wss://', 'https://');
        }
        return normalized;
    }

    private resolveNotificationTargetPanel(event: WorkflowNotificationEvent): AdminPanel | null {
        if (event.targetPanel === 'booking' || event.eventType === 'USER_TO_ADMIN' || event.eventType === 'USER_TO_ADMIN_UPDATE') {
            return 'booking';
        }
        if (event.targetPanel === 'create_room' || event.eventType === 'REJECTED_NEEDS_UPDATE') {
            return 'create_room';
        }
        if (event.targetPanel === 'export') {
            return 'export';
        }
        return null;
    }

    private removeCompletedNotifications(event: WorkflowNotificationEvent): void {
        const roomId = event.roomId;
        if (roomId == null) {
            return;
        }

        if (event.eventType === 'APPROVED' || event.eventType === 'REJECTED_NEEDS_UPDATE' || event.eventType === 'REJECTED_FINAL') {
            const completedAdminTask = event.eventType === 'APPROVED' || event.eventType === 'REJECTED_NEEDS_UPDATE' || event.eventType === 'REJECTED_FINAL';
            const completedUserTask = event.eventType === 'APPROVED' || event.eventType === 'REJECTED_FINAL';

            this.notifications = this.notifications.filter((item) => {
                if (item.roomId !== roomId) {
                    return true;
                }
                if (item.eventType === 'USER_TO_ADMIN') {
                    return !completedAdminTask;
                }
                if (item.eventType === 'USER_TO_ADMIN_UPDATE') {
                    return !completedAdminTask;
                }
                if (item.eventType === 'REJECTED_NEEDS_UPDATE') {
                    return !completedUserTask;
                }
                return true;
            });
        }
    }

    private persistNotificationsCache(): void {
        if (!isPlatformBrowser(this.platformId)) {
            return;
        }

        try {
            localStorage.setItem(this.notificationCacheKey(), JSON.stringify(this.notifications));
        } catch {
            // Ignore cache write failures.
        }
    }

    private clearNotificationsCache(): void {
        if (!isPlatformBrowser(this.platformId)) {
            return;
        }

        try {
            localStorage.removeItem(this.notificationCacheKey());
        } catch {
            // Ignore cache clear failures.
        }
    }

    // private loadNotificationsFromCache(): WorkflowNotificationEvent[] {
    //     if (!isPlatformBrowser(this.platformId)) {
    //         return [];
    //     }

    //     try {
    //         const raw = localStorage.getItem(this.notificationCacheKey());
    //         if (!raw) {
    //             return [];
    //         }
    //         const parsed = JSON.parse(raw) as WorkflowNotificationEvent[];
    //         return Array.isArray(parsed) ? parsed : [];
    //     } catch {
    //         return [];
    //     }
    // }

    private notificationCacheKey(): string {
        const role = (this.authView?.effectiveRole || 'user').toLowerCase();
        const email = this.resolveCurrentUserEmail() || 'unknown';
        return `workflow_notifications_${role}_${email}`;
    }

    private toWsBrokerUrl(baseUrl: string): string {
        const normalized = (baseUrl || '').trim().replace(/\/$/, '');
        if (!normalized) {
            return 'ws://localhost:8082';
        }
        if (normalized.startsWith('https://')) {
            return normalized.replace('https://', 'wss://');
        }
        if (normalized.startsWith('http://')) {
            return normalized.replace('http://', 'ws://');
        }
        return normalized;
    }

    private isAdminView(): boolean {
        return (this.authView?.effectiveRole || '').toLowerCase() === 'admin';
    }
}

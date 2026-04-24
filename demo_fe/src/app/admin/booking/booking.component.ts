import { HttpClient, HttpHeaders } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';

interface RoomListItem {
  id: number;
  name: string;
  status: string;
  address: string;
  price: number;
  city?: string;
}

@Component({
  selector: 'app-admin-booking',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './booking.component.html',
  styleUrl: './booking.component.css'
})
export class BookingComponent implements OnChanges {
  private readonly http = inject(HttpClient);

  @Input() users: any[] = [];
  @Input() authView: any;
  @Input() token = '';
  @Input() backendBaseUrl = 'http://localhost:8082';

  rooms: RoomListItem[] = [];
  loadingRooms = false;
  roomError = '';

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['token'] || changes['authView'] || changes['backendBaseUrl']) {
      this.loadRooms();
    }
  }

  metrics() {
    const bookingCount = Math.max(this.rooms.length, 0);
    const userCount = Math.max(this.users.length, 1);

    return [
      { label: 'Total Revenue', value: '$48,295', note: '+12.5% vs last month', color: 'positive' },
      { label: 'Active Users', value: String(userCount), note: '+8.2% vs last month', color: 'positive' },
      { label: 'Total Rooms', value: String(bookingCount), note: 'Realtime từ API', color: 'positive' },
      { label: 'Page Views', value: '284K', note: '+24.7% vs last month', color: 'positive' }
    ];
  }

  bookingRows() {
    return this.rooms.slice(0, 10).map((room) => ({
      id: room.id,
      roomName: room.name,
      location: room.city ?? '-',
      address: room.address ?? '-',
      status: room.status ?? 'UNKNOWN'
    }));
  }

  private loadRooms(): void {
    this.rooms = [];
    this.roomError = '';

    if (!this.token) {
      return;
    }

    this.loadingRooms = true;
    const endpoint = this.isAdmin()
      ? `${this.backendBaseUrl}/api/rooms/admin/rooms`
      : `${this.backendBaseUrl}/api/rooms/my`;

    this.http.get<RoomListItem[]>(endpoint, { headers: this.authHeaders() }).subscribe({
      next: (res) => {
        this.rooms = Array.isArray(res) ? res : [];
      },
      error: () => {
        this.rooms = [];
        this.roomError = 'Khong tai duoc danh sach room.';
      },
      complete: () => {
        this.loadingRooms = false;
      }
    });
  }

  private authHeaders(): HttpHeaders {
    return new HttpHeaders().set('Authorization', `Bearer ${this.token}`);
  }

  private isAdmin(): boolean {
    return (this.authView?.effectiveRole ?? '').toLowerCase() === 'admin';
  }
}

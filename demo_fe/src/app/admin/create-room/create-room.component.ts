import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

type RoomStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

interface RoomFormPayload {
  name: string;
  description: string;
  phoneNumber: string;
  price: number | null;
  capacity: number | null;
  bedType: string;
  thumbnail: string;
  address: string;
  city: string;
  district: string;
}

@Component({
  selector: 'app-admin-create-room',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './create-room.component.html',
  styleUrl: './create-room.component.css'
})
export class CreateRoomComponent implements OnChanges {
  private readonly http = inject(HttpClient);

  @Input() authView: { effectiveRole: string } = { effectiveRole: 'user' };
  @Input() token = '';
  @Input() userInfo: any = null;
  @Input() backendBaseUrl = 'http://localhost:8082';

  loading = false;
  message = '';
  error = '';
  adminTasks: any[] = [];
  adminRooms: any[] = [];
  myRooms: any[] = [];
  myUpdateTasks: any[] = [];
  selectedUpdateTaskId = '';
  form: RoomFormPayload = this.emptyForm();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['token'] || changes['authView']) {
      this.refreshData();
    }
  }

  isAdmin(): boolean {
    return (this.authView?.effectiveRole ?? '').toLowerCase() === 'admin';
  }

  submit(): void {
    this.error = '';
    this.message = '';
    if (!this.form.name.trim() || !this.form.price || !this.form.capacity) {
      this.error = 'Vui long nhap day du ten phong, gia va suc chua.';
      return;
    }
    if (!this.token) {
      this.error = 'Chua co token dang nhap.';
      return;
    }

    const payload = {
      ...this.form,
      price: Number(this.form.price),
      capacity: Number(this.form.capacity)
    };

    this.loading = true;
    const headers = this.authHeaders();
    const request$ = this.selectedUpdateTaskId
      ? this.http.post(`${this.backendBaseUrl}/api/rooms/my/update-tasks/${this.selectedUpdateTaskId}/resubmit`, payload, { headers })
      : this.http.post(`${this.backendBaseUrl}/api/rooms/submit`, payload, { headers });

    request$.subscribe({
      next: (res: any) => {
        this.message = res?.message ?? 'Thao tac thanh cong.';
        this.form = this.emptyForm();
        this.selectedUpdateTaskId = '';
        this.refreshData();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Khong the gui thong tin phong.';
        this.loading = false;
      },
      complete: () => {
        this.loading = false;
      }
    });
  }

  onUpdateTaskSelection(taskId: string): void {
    this.selectedUpdateTaskId = taskId;
    this.hydrateFormFromSelectedTask();
  }

  approve(taskId: string, approved: boolean): void {
    if (!this.token) return;
    this.loading = true;
    this.error = '';
    this.message = '';
    this.http.post(
      `${this.backendBaseUrl}/api/rooms/admin/tasks/${taskId}/decision`,
      { approved },
      { headers: this.authHeaders() }
    ).subscribe({
      next: (res: any) => {
        this.message = res?.message ?? 'Da xu ly task.';
        this.refreshData();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Xu ly task that bai.';
        this.loading = false;
      },
      complete: () => {
        this.loading = false;
      }
    });
  }

  roomStatusClass(status: RoomStatus): string {
    if (status === 'APPROVED') return 'status-approved';
    if (status === 'REJECTED') return 'status-rejected';
    return 'status-pending';
  }

  private refreshData(): void {
    this.adminTasks = [];
    this.adminRooms = [];
    this.myRooms = [];
    this.myUpdateTasks = [];
    if (!this.token) return;

    if (this.isAdmin()) {
      this.http.get<any[]>(`${this.backendBaseUrl}/api/rooms/admin/tasks`, { headers: this.authHeaders() }).subscribe({
        next: (res) => this.adminTasks = Array.isArray(res) ? res : [],
        error: () => this.adminTasks = []
      });
      this.http.get<any[]>(`${this.backendBaseUrl}/api/rooms/admin/rooms`, { headers: this.authHeaders() }).subscribe({
        next: (res) => this.adminRooms = Array.isArray(res) ? res : [],
        error: () => this.adminRooms = []
      });
      return;
    }

    this.http.get<any[]>(`${this.backendBaseUrl}/api/rooms/my`, { headers: this.authHeaders() }).subscribe({
      next: (res) => {
        this.myRooms = Array.isArray(res) ? res : [];
        this.hydrateFormFromSelectedTask();
      },
      error: () => this.myRooms = []
    });
    this.http.get<any[]>(`${this.backendBaseUrl}/api/rooms/my/update-tasks`, { headers: this.authHeaders() }).subscribe({
      next: (res) => this.myUpdateTasks = Array.isArray(res) ? res : [],
      error: () => this.myUpdateTasks = []
    });
  }

  private authHeaders(): HttpHeaders {
    return new HttpHeaders().set('Authorization', `Bearer ${this.token}`);
  }

  private hydrateFormFromSelectedTask(): void {
    if (!this.selectedUpdateTaskId) {
      this.form = this.emptyForm();
      return;
    }

    const selectedTask = this.myUpdateTasks.find((task) => task?.taskId === this.selectedUpdateTaskId);
    if (!selectedTask) {
      return;
    }

    const room = this.myRooms.find((item) => item?.id === selectedTask?.roomId);
    if (!room) {
      this.form = {
        ...this.emptyForm(),
        name: selectedTask?.roomName ?? ''
      };
      return;
    }

    this.form = {
      name: room?.name ?? '',
      description: room?.description ?? '',
      phoneNumber: room?.user?.phoneNumber ?? '',
      price: room?.price ?? null,
      capacity: room?.capacity ?? null,
      bedType: room?.bedType ?? '',
      thumbnail: room?.thumbnail ?? '',
      address: room?.address ?? '',
      city: room?.city ?? '',
      district: room?.district ?? ''
    };
  }

  private emptyForm(): RoomFormPayload {
    return {
      name: '',
      description: '',
      phoneNumber: '',
      price: null,
      capacity: null,
      bedType: '',
      thumbnail: '',
      address: '',
      city: '',
      district: ''
    };
  }
}

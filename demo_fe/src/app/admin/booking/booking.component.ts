import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-admin-booking',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './booking.component.html',
  styleUrl: './booking.component.css'
})
export class BookingComponent {
  @Input() users: any[] = [];
  @Input() authView: any;

  metrics() {
    const bookingCount = Math.max(this.users.length, 8);
    const userCount = Math.max(this.users.length, 1);
    
    return [
        { label: 'Total Revenue', value: '$48,295', note: '+12.5% vs last month', color: 'positive' },
        { label: 'Active Users', value: String(userCount), note: '+8.2% vs last month', color: 'positive' },
        { label: 'Total Orders', value: String(bookingCount), note: '-3.1% vs last month', color: 'negative' },
        { label: 'Page Views', value: '284K', note: '+24.7% vs last month', color: 'positive' }
    ];
  }

  bookingRows() {
     return [
       { id: '#1', customer: 'Nguyen Van A', service: 'Standard Room', date: '2026-04-10', status: 'Confirmed' },
       { id: '#2', customer: 'Tran Thi B', service: 'Deluxe Room', date: '2026-04-11', status: 'Pending' },
       { id: '#3', customer: 'Le Van C', service: 'Suite', date: '2026-04-12', status: 'Draft' },
       { id: '#4', customer: 'Pham Ngoc D', service: 'Single Room', date: '2026-04-13', status: 'Confirmed' }
     ];
  }
}

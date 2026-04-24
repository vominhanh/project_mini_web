import { Routes } from '@angular/router';
import { AppComponent } from './app.component';

export const routes: Routes = [
  {
    path: '',
    component: AppComponent,
  },
  {
    path: 'admin',
    component: AppComponent,
  },
  {
    path: '**',
    component: AppComponent,
  },
];

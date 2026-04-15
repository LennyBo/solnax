import { Component, DestroyRef, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ChargeSessionService } from '../../service/charge-session.service';
import { ChargeSession } from '../../model/charge-session';

@Component({
  selector: 'app-charge-session-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './charge-session-list.html',
  styleUrl: './charge-session-list.scss',
})
export class ChargeSessionList implements OnInit {

  sessions = signal<ChargeSession[]>([]);
  loading = signal<boolean>(false);

  constructor(
    private chargeSessionService: ChargeSessionService,
    private destroyRef: DestroyRef
  ) {}

  ngOnInit() {
    this.loadSessions();
  }

  loadSessions() {
    this.loading.set(true);
    this.chargeSessionService.getRecent()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: sessions => {
          this.sessions.set(sessions);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
        }
      });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'status-active';
      case 'COMPLETED': return 'status-completed';
      case 'ABORTED': return 'status-aborted';
      default: return '';
    }
  }

  formatDuration(session: ChargeSession): string {
    if (!session.startedAt) return '-';
    const start = new Date(session.startedAt);
    const end = session.endedAt ? new Date(session.endedAt) : new Date();
    const diffMs = end.getTime() - start.getTime();
    const hours = Math.floor(diffMs / 3600000);
    const minutes = Math.floor((diffMs % 3600000) / 60000);
    return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
  }
}


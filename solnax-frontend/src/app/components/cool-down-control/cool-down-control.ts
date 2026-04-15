import { Component, DestroyRef, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CoolDownService } from '../../service/cool-down.service';
import { CoolDownStatus } from '../../model/cool-down-status';

@Component({
  selector: 'app-cool-down-control',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './cool-down-control.html',
  styleUrl: './cool-down-control.scss',
})
export class CoolDownControl implements OnInit {

  constructor(
    private coolDownService: CoolDownService,
    private destroyRef: DestroyRef
  ) {}

  /** STATE */
  active = signal<boolean>(false);
  loading = signal<boolean>(false);
  toast = signal<{ message: string; type: 'success' | 'error' | 'info' } | null>(null);
  cooldowns = signal<CoolDownStatus[]>([]);

  ngOnInit() {
    this.refreshStatus();
    this.loadCooldownStatus();
  }

  /** LOAD STATUS */
  private refreshStatus() {
    this.loading.set(true);
    this.coolDownService.isActive()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: status => {
          this.active.set(status);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.showToast('Failed to fetch cooldown status', 'error');
        }
      });
  }

  /** LOAD ACTIVE COOLDOWNS */
  loadCooldownStatus() {
    this.coolDownService.getStatus()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: statuses => this.cooldowns.set(statuses),
        error: () => {} // silent fail
      });
  }

  /** ACTIONS */
  createCoolDown() {
    if (this.active()) return;

    this.loading.set(true);
    this.coolDownService.create()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.active.set(true);
          this.loading.set(false);
          this.showToast('Cooldown activated until tomorrow', 'success');
          this.loadCooldownStatus();
        },
        error: err => {
          this.loading.set(false);
          if (err.status === 409) {
            this.active.set(true);
            this.showToast('Cooldown already active', 'info');
          } else {
            this.showToast('Failed to activate cooldown', 'error');
          }
        }
      });
  }

  clearCoolDown() {
    this.loading.set(true);
    this.coolDownService.clear()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.active.set(false);
          this.loading.set(false);
          this.showToast('Cooldown cleared', 'success');
          this.cooldowns.set([]);
        },
        error: () => {
          this.loading.set(false);
          this.showToast('Failed to clear cooldown', 'error');
        }
      });
  }

  /** TOAST */
  private showToast(message: string, type: 'success' | 'error' | 'info') {
    this.toast.set({ message, type });
    setTimeout(() => this.toast.set(null), 3000);
  }

  formatReason(reason: string): string {
    switch (reason) {
      case 'MANUAL': return '🛑 Manual';
      case 'NOT_CONNECTED': return '🔌 Not Connected';
      case 'FULL': return '🔋 Battery Full';
      case 'LOW_BATTERY': return '🪫 Low Battery';
      case 'NO_RESPONSE': return '📡 No Response';
      case 'UNABLE_TO_CHARGE': return '⚠️ Unable to Charge';
      default: return reason;
    }
  }
}

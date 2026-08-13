import { Component, DestroyRef, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CoolDownService } from '../../service/cool-down.service';
import { CoolDownStatus } from '../../model/cool-down-status';
import { ChargeControlMode, ChargeControlModeName } from '../../model/charge-control-mode';

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
  mode = signal<ChargeControlModeName>('NORMAL');
  endsAt = signal<string | null>(null);
  loading = signal<boolean>(false);
  toast = signal<{ message: string; type: 'success' | 'error' | 'info' } | null>(null);
  cooldowns = signal<CoolDownStatus[]>([]);

  /** A mode other than NORMAL is active */
  active = computed(() => this.mode() !== 'NORMAL');

  ngOnInit() {
    this.refreshMode();
    this.loadCooldownStatus();
  }

  /** LOAD MODE */
  private refreshMode() {
    this.loading.set(true);
    this.coolDownService.getMode()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: status => {
          this.applyMode(status);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.showToast('Failed to fetch the charge control mode', 'error');
        }
      });
  }

  private applyMode(status: ChargeControlMode) {
    this.mode.set(status?.mode ?? 'NORMAL');
    this.endsAt.set(status?.endsAt ?? null);
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
  activate(mode: ChargeControlModeName) {
    if (this.mode() === mode) return;

    this.loading.set(true);
    this.coolDownService.activateMode(mode)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: status => {
          this.applyMode(status);
          this.loading.set(false);
          this.showToast(`${this.formatMode(mode)} activated until tomorrow`, 'success');
          this.loadCooldownStatus();
        },
        error: err => {
          this.loading.set(false);
          if (err.status === 409) {
            this.mode.set(mode);
            this.showToast(`${this.formatMode(mode)} is already active`, 'info');
          } else {
            this.showToast(`Failed to activate ${this.formatMode(mode)}`, 'error');
          }
        }
      });
  }

  backToNormal() {
    this.loading.set(true);
    this.coolDownService.clearMode()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.mode.set('NORMAL');
          this.endsAt.set(null);
          this.loading.set(false);
          this.showToast('Back to normal solar optimization', 'success');
          this.loadCooldownStatus();
        },
        error: () => {
          this.loading.set(false);
          this.showToast('Failed to clear the mode', 'error');
        }
      });
  }

  clearCoolDown() {
    this.loading.set(true);
    this.coolDownService.clearAll()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.mode.set('NORMAL');
          this.endsAt.set(null);
          this.loading.set(false);
          this.showToast('All cooldowns cleared', 'success');
          this.cooldowns.set([]);
        },
        error: () => {
          this.loading.set(false);
          this.showToast('Failed to clear cooldowns', 'error');
        }
      });
  }

  /** TOAST */
  private showToast(message: string, type: 'success' | 'error' | 'info') {
    this.toast.set({ message, type });
    setTimeout(() => this.toast.set(null), 3000);
  }

  formatMode(mode: ChargeControlModeName): string {
    switch (mode) {
      case 'MANUAL': return '🛑 Manual';
      case 'ECO_PLUS': return '🌱 Eco+';
      default: return '☀️ Solar optimization';
    }
  }

  modeDescription(): string {
    switch (this.mode()) {
      case 'MANUAL': return 'Solnax will not start, stop or adjust the charge';
      case 'ECO_PLUS': return 'Follows solar, never stops the charge — falls back to the lowest speed, even overnight';
      default: return 'Charging follows the solar surplus';
    }
  }

  formatReason(reason: string): string {
    switch (reason) {
      case 'MANUAL': return '🛑 Manual';
      case 'ECO_PLUS': return '🌱 Eco+';
      case 'NOT_CONNECTED': return '🔌 Not Connected';
      case 'FULL': return '🔋 Battery Full';
      case 'LOW_BATTERY': return '🪫 Low Battery';
      case 'NO_RESPONSE': return '📡 No Response';
      default: return reason;
    }
  }
}

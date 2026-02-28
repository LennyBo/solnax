import { Component, DestroyRef, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CoolDownService } from '../../service/cool-down.service';

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

  ngOnInit() {
    this.refreshStatus();
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
}

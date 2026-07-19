import { inject, type InjectionKey } from 'vue';

export interface ToastApi {
  error(message: string): void;
  success(message: string): void;
}

export const toastKey: InjectionKey<ToastApi> = Symbol('toast');

export function useToast(): ToastApi {
  const toast = inject(toastKey);
  if (!toast) throw new Error('Toast 服务未初始化');
  return toast;
}

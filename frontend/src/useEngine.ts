import { useSyncExternalStore } from 'react';
import { Engine } from './engine/engine';

/**
 * 엔진은 프로토타입과 동일하게 가변 인스턴스 하나로 두고,
 * version 카운터로만 React를 다시 그린다.
 */
export const engine = new Engine();

export function useEngine() {
  useSyncExternalStore(engine.subscribe, engine.getSnapshot);
  return engine;
}

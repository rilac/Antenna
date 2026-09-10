/* 자동 생성 — 손으로 고치지 말 것.
 * 원본 시트를 자르는 스크립트가 frames.json 과 함께 낸다.
 * 자산을 다시 뽑으면 이 파일도 같이 바뀐다. */
const A = '/assets/blockchain-animation/'

/** 스프라이트 한 장의 크기. 모든 동작이 이 캔버스를 공유한다 —
 *  그래야 달리기에서 줍기로 넘어갈 때 발이 어긋나지 않는다. */
export const CANVAS = { w: 339, h: 373 }

/** 캔버스 안에서 캐릭터가 서는 자리. x 는 몸 중심, y 는 발끝 */
export const BASELINE = { x: 147, y: 365 }

export const RUN_EMPTY = [
  A + 'mascot/run-empty-1.webp',
  A + 'mascot/run-empty-2.webp',
  A + 'mascot/run-empty-3.webp',
  A + 'mascot/run-empty-4.webp',
  A + 'mascot/run-empty-5.webp',
  A + 'mascot/run-empty-6.webp',
  A + 'mascot/run-empty-7.webp',
  A + 'mascot/run-empty-8.webp',
]
export const RUN_CARRY = [
  A + 'mascot/run-carry-1.webp',
  A + 'mascot/run-carry-2.webp',
  A + 'mascot/run-carry-3.webp',
  A + 'mascot/run-carry-4.webp',
  A + 'mascot/run-carry-5.webp',
  A + 'mascot/run-carry-6.webp',
  A + 'mascot/run-carry-7.webp',
  A + 'mascot/run-carry-8.webp',
]
export const PICKUP = [
  A + 'mascot/pickup-1.webp',
  A + 'mascot/pickup-2.webp',
  A + 'mascot/pickup-3.webp',
  A + 'mascot/pickup-4.webp',
]
export const PLACE = [
  A + 'mascot/place-1.webp',
  A + 'mascot/place-2.webp',
  A + 'mascot/place-3.webp',
  A + 'mascot/place-4.webp',
]
export const IDLE = [
  A + 'mascot/idle-1.webp',
  A + 'mascot/idle-2.webp',
]

/** 서서 기다리는 자세. a1(왼쪽 보기)을 뒤집은 IDLE 은 더듬이 색이 좌우로
 *  바뀌어 달리기 프레임과 안 맞는다 — 원래 오른쪽을 보는 이 한 장을 쓴다. */
export const STAND = A + 'mascot/stand.webp'
export const CHEER = A + 'mascot/cheer.webp'

/** 스프라이트 안 소품의 자리. 캐릭터 기준 좌우 거리와 폭(캔버스 단위).
 *  별도 레이어 큐브를 여기 맞춰 놓아야 줍기로 넘어갈 때 튀지 않는다. */
export const PROP = {
  cube: { dx: 106, w: 90 },
  slot: { dx: 100, w: 164 },
}

export const CUBE = {
  draft: A + 'cube/cube-draft.webp',
  slot:  A + 'cube/slot-empty.webp',
}

/** 슬롯에 얹힌 큐브. 서버가 말한 앵커 상태와 짝이 맞는다 */
export const DOCKED = {
  sealed:   A + 'cube/docked-sealed.webp',
  anchored: A + 'cube/docked-anchored.webp',
  failed:   A + 'cube/docked-failed.webp',
}

export const FX = {
  sparkSmall:  A + 'effect/spark-small.webp',
  sparkBig:    A + 'effect/spark-big.webp',
  ringActive:  A + 'effect/ring-active.webp',
  chainIdle:   A + 'effect/chain-idle.webp',
  chainActive: A + 'effect/chain-active.webp',
}

/** 미리 받아 둘 것 전부. 첫 재생에서 프레임이 비면 애니메이션이 끊겨 보인다 */
export const ALL = [
  ...RUN_EMPTY, ...RUN_CARRY, ...PICKUP, ...PLACE, ...IDLE,
  ...Object.values(CUBE), ...Object.values(DOCKED), ...Object.values(FX),
]

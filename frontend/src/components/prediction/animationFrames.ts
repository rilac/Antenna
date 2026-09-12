/* 자동 생성 — 손으로 고치지 말 것.
 * personal/animation-source/prep-ani2.py 가 원본 시트를 자를 때 함께 낸다.
 * 자산을 다시 뽑으면 이 파일도 같이 바뀐다. */
const A = '/assets/blockchain-animation/'

/** 마스코트 스프라이트 한 장의 크기. 모든 동작이 이 캔버스를 공유한다 —
 *  그래야 걷기에서 줍기로 넘어갈 때 발이 어긋나지 않는다. */
export const CANVAS = { w: 278, h: 311 }

/** 캔버스 안에서 캐릭터가 서는 자리. x 는 몸 중심, y 는 발끝 */
export const BASELINE = { x: 138, y: 303 }

/** 빈손으로 걷는다. 건물에서 블록까지 */
export const WALK_EMPTY = [
  A + 'mascot/walk-empty-1.webp',
  A + 'mascot/walk-empty-2.webp',
  A + 'mascot/walk-empty-3.webp',
  A + 'mascot/walk-empty-4.webp',
  A + 'mascot/walk-empty-5.webp',
  A + 'mascot/walk-empty-6.webp',
]

/** 블록을 안고 걷는다. 블록에서 포털까지 — 이 구간이 제일 길다 */
export const WALK_CARRY = [
  A + 'mascot/walk-carry-1.webp',
  A + 'mascot/walk-carry-2.webp',
  A + 'mascot/walk-carry-3.webp',
  A + 'mascot/walk-carry-4.webp',
  A + 'mascot/walk-carry-5.webp',
  A + 'mascot/walk-carry-6.webp',
]

/** 블록을 받침대에 내려놓는다. 다가섬 - 숙임 - 올려놓음 - 환호 네 박자라
 *  걷기에서 바로 이어진다. 스프라이트 안에 받침대가 함께 그려져 있는데,
 *  다리와 겹쳐 그려져 있어 그림에서 떼어낼 수 없다(연결 덩어리로도,
 *  침식 6회로도 갈라지지 않았다). 그래서 화면에서는 포털을 그 자리에
 *  겹쳐 그려 가린다 - BlockchainScene 의 PED_DX 가 그 자리다. */
export const PLACE = [
  A + 'mascot/place-1.webp',
  A + 'mascot/place-2.webp',
  A + 'mascot/place-3.webp',
  A + 'mascot/place-4.webp',
]

/** 환호. 정면을 보고 있어 옆으로 걷는 동작과는 이어지지 않는다 —
 *  마스코트가 멈춘 뒤(확정 순간)에만 쓴다. */
export const CHEER = [
  A + 'mascot/cheer-1.webp',
  A + 'mascot/cheer-2.webp',
  A + 'mascot/cheer-3.webp',
  A + 'mascot/cheer-4.webp',
  A + 'mascot/cheer-5.webp',
  A + 'mascot/cheer-6.webp',
]

/** 블록을 넣고 돌아서서 지켜보는 자세. 정면이라 환호와 이어진다 */
export const WATCH = A + 'mascot/pose-1.webp'

/** 건물 그림에서 입구가 있는 자리(폭에 대한 비율).
 *  개미가 나오는 x 를 여기에 맞춘다 — 눈대중으로 정하면 건물 그림을
 *  바꿀 때마다 개미가 벽에서 튀어나온다. 자르는 스크립트가 터널의
 *  어두운 구역을 찾아 잰 값이다. */
export const DOOR = 0.582

/** 놓는 자리. 놓기 스프라이트 안에 그려진 받침대를 그대로 떼어낸 것이라,
 *  개미가 블록을 내려놓는 순간 그림이 정확히 맞물린다 — 따로 그린 목적지를
 *  쓰면 받침대가 둘이 되거나 놓는 장면이 가려진다.
 *  dx  몸 중심에서 오른쪽으로 떨어진 거리(캔버스 px)
 *  w   폭(캔버스 px). 화면에서는 마스코트와 같은 배율로 줄인다
 *  top 바닥선 위로 윗면까지의 높이. 블록이 얹히는 높이다 */
export const PAD = { dx: 34, w: 173, top: 69 }

/** 완성 슬롯 크기(캔버스 px). 밑동 너비는 받침대와 같고 홀로그램만큼 높다 */
export const SLOT = { w: 173, h: 158 }

/** 무대 양 끝. 예측 데이터가 나오는 곳과 체인에 묶이는 곳 */
export const SCENE = {
  building: A + 'scene/building.webp',
  pad:      A + 'scene/pad.webp',
  slot:     A + 'scene/slot.webp',    // 확정된 뒤 받침대가 바뀌는 모습
}

/** 끝난 뒤 포털 안에 남는 블록. 기다리는 동안에는 아무것도 띄우지 않으므로
 *  끝난 상태 둘만 있으면 된다 — 대기 중에 블록을 띄우면 이미 자리를 잡은
 *  것처럼 보이고, 확정됐을 때 달라지는 것이 없어진다. */
export const CUBE = {
  anchored: A + 'cube/cube-anchored.webp',  // CONFIRMED
  failed:   A + 'cube/cube-failed.webp',    // FAILED
}

/** 포털 위에 엮이는 사슬. 블록이 하나씩 늘어난다 —
 *  앵커 배치는 여러 사람의 예측을 한 번에 묶으므로, 사슬이 자라는 것은
 *  지어낸 진행이 아니라 실제로 일어나는 일이다. */
export const CHAIN = [
  { src: A + 'effect/chain-1.webp', w: 124 },
  { src: A + 'effect/chain-2.webp', w: 226 },
  { src: A + 'effect/chain-3.webp', w: 263 },
  { src: A + 'effect/chain-4.webp', w: 342 },
]

export const FX = {
  sparkSmall: A + 'effect/spark-small.webp',
  sparkBig:   A + 'effect/spark-big.webp',
  ring:       A + 'effect/ring.webp',      // 블록이 포털에 얹히는 순간
}

/** 미리 받아 둘 것 전부. 첫 재생에서 프레임이 비면 애니메이션이 끊겨 보인다 */
export const ALL = [
  ...WALK_EMPTY, ...WALK_CARRY, ...PLACE, ...CHEER, WATCH,
  ...CHAIN.map((c) => c.src),
  ...Object.values(SCENE), ...Object.values(CUBE), ...Object.values(FX),
]

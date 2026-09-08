/* M-02 온체인 처리 대기. 설계서 §4 M-02 · §3 원칙 4.

   202 를 쓰는 네 곳(C-01 슬롯 초과 · E-02 구독 · G-03 참가 · H-04 광고)이
   이 하나를 공유한다. 화면마다 폴링을 새로 쓰면 간격·중단 조건·실패 처리가
   제각각이 되고, 특히 **언제 멈추는가** 를 빠뜨리기 쉽다.

   GET /operations/{operationId}  백엔드 구현 완료 — 목업이 아니다.

   왜 폴링인가
   서버가 상태를 밀어 주는 채널이 없다. 전이는 체인 인덱서가 chain_events 를
   쓰면서 함께 하고(서버 Operation.Status 주석), 서버 쪽 폴링 배치도 없다.
   그래서 결과를 알 방법이 조회뿐이다. */
import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from './client'
import type { ApiError } from './errors'

/** 202 를 쓰는 네 곳. POST /wallet/nonce 의 scope 어휘와 같다(WALLET_LINK 만 빠진다) */
export const OPERATION_KINDS = ['PREDICTION_BURN', 'SUBSCRIBE', 'AD', 'SEASON_JOIN'] as const
export type OperationKind = (typeof OPERATION_KINDS)[number]

export const OPERATION_STATUSES = ['PENDING', 'SUCCEEDED', 'FAILED'] as const
export type OperationStatus = (typeof OPERATION_STATUSES)[number]

/** 성공하면 무엇이 만들어졌는지. 대상별 필드 넷 대신 타입+id 로 가리킨다 */
export type OperationResource = {
  type: 'SUBSCRIPTION' | 'AD' | 'PREDICTION' | 'SEASON_PARTICIPANT'
  id: number
}

/** 서버 OperationResponse 와 짝이다. resource · error · txHash 는 없으면 아예 오지 않는다 */
export type Operation = {
  operationId: string
  kind: OperationKind
  status: OperationStatus
  resource?: OperationResource
  txHash?: string
  /** FAILED 일 때만. code 어휘는 인덱서가 만들어 서버 enum 이 아니다 */
  error?: { code: string; message: string }
  createdAt: string
  settledAt?: string
}

export function fetchOperation(operationId: string) {
  return api.get<Operation>(`/operations/${operationId}`)
}

/* PENDING 은 "블록 확정 대기" 다(설계서 §3 원칙 4). 설계서가 3~5초를 적어 두었고
   그 안에서 4초를 쓴다 — 3초는 사설망 블록 시간(2초)과 어긋나 헛질문이 늘고,
   5초는 이미 성공했는데도 기다리는 시간이 눈에 띄게 길어진다. */
const INTERVAL_MS = 4000

/* 언제 멈추는가. 체인이 막히면 PENDING 이 끝없이 이어질 수 있어 상한을 둔다.
   4초 × 45 = 3분. 넘으면 실패로 단정하지 않고 "확인이 늦어진다" 로 넘긴다 —
   여기서 FAILED 라고 말하면 실제로는 성공한 결제를 실패로 알리게 된다. */
const MAX_ATTEMPTS = 45

export type OperationState = {
  operation: Operation | null
  /** 아직 PENDING 이라 계속 묻는 중 */
  polling: boolean
  /** 상한을 넘겨 그만 물었다. 실패가 아니라 판정 보류다 */
  timedOut: boolean
  error: ApiError | null
}

const IDLE: OperationState = { operation: null, polling: false, timedOut: false, error: null }

/**
 * operationId 가 들어오면 확정될 때까지 묻는다. null 이면 아무것도 하지 않는다.
 *
 * 호출부는 202 를 받은 순간 id 를 넘기고, status 가 SUCCEEDED 로 바뀌는 것을 보고
 * 다음 단계로 넘어가면 된다.
 */
export function useOperation(operationId: string | null) {
  const [state, setState] = useState<OperationState>(IDLE)
  /* 다시 확인은 한 번 묻고 끝나면 안 된다 — 그때도 PENDING 이면 다시 기다려야 한다.
     그래서 조회를 따로 부르지 않고 이 값을 올려 폴링 자체를 처음부터 다시 돌린다. */
  const [round, setRound] = useState(0)
  /* 늦게 온 이전 폴링이 최신 결과를 덮지 않게 세대를 센다 — useAsync 와 같은 장치다 */
  const gen = useRef(0)

  useEffect(() => {
    const mine = ++gen.current
    if (!operationId) return

    let timer: ReturnType<typeof setTimeout> | undefined
    let attempts = 0
    let stopped = false

    async function ask(id: string) {
      try {
        const operation = await fetchOperation(id)
        if (stopped || mine !== gen.current) return

        if (operation.status === 'PENDING') {
          attempts += 1
          if (attempts >= MAX_ATTEMPTS) {
            setState({ operation, polling: false, timedOut: true, error: null })
            return
          }
          setState({ operation, polling: true, timedOut: false, error: null })
          timer = setTimeout(() => { void ask(id) }, INTERVAL_MS)
          return
        }
        // SUCCEEDED · FAILED 는 종착이다. 더 묻지 않는다.
        setState({ operation, polling: false, timedOut: false, error: null })
      } catch (e) {
        if (stopped || mine !== gen.current) return
        /* 조회가 실패했다고 결제가 실패한 것은 아니다. 그래서 status 를 건드리지 않고
           오류만 얹는다 — 화면이 "다시 확인" 을 줄 수 있게. */
        setState((s) => ({ ...s, polling: false, error: e as ApiError }))
      }
    }

    void ask(operationId)
    return () => {
      stopped = true
      clearTimeout(timer)
    }
  }, [operationId, round])

  /** 시간 초과·조회 실패 뒤 사용자가 다시 확인할 때. 시도 횟수도 0 부터 다시 센다 */
  const recheck = useCallback(() => {
    if (!operationId) return
    setRound((n) => n + 1)
  }, [operationId])

  return { ...state, recheck }
}

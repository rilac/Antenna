/* M-09 온보딩 튜토리얼 (오버레이 · 라우트 없음)
   담당 스토리 [ANT-FE-LOGIN]
   설계서 docs/화면설계서.md §3 M · §4 M-09

   설계 제약
   - **라우트를 주지 않는다.** 라우트면 히스토리에 남아 이탈 시 복귀가 어렵다.
     셸(Layout) 위 오버레이로만 뜬다 — 홈에만 걸면 비로그인 딥링크로 막혔다가
     가입한 회원이 못 본다(그 사람은 막혔던 경로로 착지한다).
   - **isNew 는 최초 1회뿐이다.** 건너뛰면 다시 뜨지 않는다 — 남은 단계는
     H-03 환경 설정의 "아직 마치지 않은 설정" 이 받는다.
   - **M-01 지갑 연동을 이 안에서 열지 않는다.** 모달 3겹이 되기 때문이다.
     연동을 누르면 onLinkWallet 으로 올려 호출부(셸)가 이걸 닫고 띄운다.
     WalletLinkModal 머리말에도 같은 계약이 적혀 있다.

   닉네임 입력을 두지 않는 이유
   설계서 §4 M-09 는 이 화면의 API 로 닉네임 확인·저장을 함께 적고 있다. 그건
   온보딩이 한 덩어리였을 때의 목록이고, 실제로는 A-02(/onboarding/nickname)가
   차단 라우트로 갈라져 나갔다 — RequireAccess 가 닉네임 없는 회원을 그리로
   돌려보내므로 **이 오버레이가 뜨는 시점에 닉네임은 이미 있다.** 여기서 폼을 다시 두면
   같은 일을 두 곳에서 하고, 무엇보다 도달할 수 없는 입력이 된다.
   그래서 닉네임은 "끝난 단계" 로 보여주고, 남은 것(지갑)만 할 일로 남긴다.

   지갑 상태를 GET /wallet 으로 다시 묻지 않는 이유
   같은 사실(linked)이 로그인·세션 복원에서 읽는 /users/me 에 이미 실려 있고
   셸이 walletLinked 로 들고 있다. 한 번 더 물으면 요청만 늘고, 두 값이 어긋날
   때 어느 쪽이 참인지 화면이 판단해야 한다. */
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/context'
import '../styles/screens/onboarding-tour.css'

type Props = {
  onClose: () => void
  /** 지갑 연동을 누르면 올린다. 호출부가 이 오버레이를 닫고 M-01 을 띄운다. */
  onLinkWallet: () => void
}

/* 걸음마다 무엇을 알려 줄지. 마지막 걸음에서만 할 일이 생긴다 —
   앞의 둘은 읽고 넘기는 자리다. */
const STEPS = [
  {
    key: 'what',
    title: '예측을 기록으로 남깁니다',
    body: '종목의 방향과 목표가를 정해 예측을 등록하면, 그 순간의 판단이 그대로 잠깁니다. 나중에 고칠 수 없습니다.',
  },
  {
    key: 'proof',
    title: '고치지 않았다는 걸 증명합니다',
    body: '등록한 예측은 커밋 해시로 봉인되고 블록체인에 올라갑니다. 만기가 지나면 결과와 오차가 자동으로 판정됩니다.',
  },
  {
    /* 모의투자는 셸이 모드로 가르는 제품의 절반이다(Layout 의 modeswitch).
       예측만 안내하고 끝내면 새 회원이 절반을 못 본 채 나간다.

       문구는 G-02 모드 선택 카드에 적힌 값만 옮겼다 — 연습은 "원하는 때 직접
       진행", 대회는 "1시간마다 자동 진행 · 랭킹 반영"이다. 없는 기능을
       약속하지 않으려고 여기서 새 표현을 만들지 않았다. */
    key: 'sim',
    title: '과거 시세로 먼저 연습할 수 있습니다',
    body: '모의투자는 지난 장을 되돌려 매매해 보는 곳입니다. 연습은 원하는 때 직접 진행하고, 대회는 1시간마다 자동으로 넘어가며 랭킹에 반영됩니다.',
    /* 자리를 "왼쪽 위" 로 못 박지 않는다. 901px 아래에서는 레일이 접혀
       모드 스위치가 메뉴 버튼 뒤로 들어가므로(Layout 의 railOpen) 그 문구가
       틀린 안내가 된다. 메뉴 안에 있다는 것까지만 말한다. */
    where: '메뉴의 "모의 투자"에서 언제든 시작할 수 있습니다.',
  },
  {
    key: 'wallet',
    title: '지갑을 연동해 주세요',
    body: '예측을 등록하려면 지갑 서명이 필요합니다. 연동은 지금 해도 되고, 첫 예측을 등록할 때 해도 됩니다.',
  },
] as const

export default function OnboardingTour({ onClose, onLinkWallet }: Props) {
  /* user 는 null 일 수 있는 타입이지만, 셸이 authed·nickname 을 확인한 뒤에만
     띄우므로 여기서는 이미 확정된 값이다. 그래도 타입을 우겨 넣지 않고 옵셔널로
     읽는다 — 그 조건이 바뀌면 여기서 터진다. */
  const { user } = useAuth()
  const nickname = user?.nickname ?? ''
  const walletLinked = user?.walletLinked ?? false
  const [at, setAt] = useState(0)
  const dialogRef = useRef<HTMLDivElement>(null)

  const step = STEPS[at]
  const last = at === STEPS.length - 1

  /* 배경 클릭으로는 닫지 않는다. 로그인 직후 딱 한 번 뜨는 안내라, 실수로
     스쳐 닫으면 다시 볼 길이 없다(라우트가 없으므로 되돌아올 수도 없다).
     닫기는 X 와 "건너뛰기" 로만 — 둘 다 닫는다는 뜻이 분명한 자리다. */
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  // 오버레이가 열리면 초점을 안으로 들인다. 없으면 탭이 뒤 화면을 돌아다닌다.
  useEffect(() => { dialogRef.current?.focus() }, [])

  const next = useCallback(() => setAt((i) => Math.min(i + 1, STEPS.length - 1)), [])

  return (
    <div className="ob-backdrop">
      <div
        className="ob-modal" role="dialog" aria-modal="true" aria-labelledby="ob-title"
        tabIndex={-1} ref={dialogRef}
      >
        <div className="ob-head">
          <p className="ob-hello">
            {nickname ? `${nickname} 님, 환영합니다` : '환영합니다'}
          </p>
          <button type="button" className="ob-x" aria-label="건너뛰기" onClick={onClose}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        {/* 걸음 표시. 몇 걸음 남았는지 모르면 건너뛸지 읽을지 판단할 수 없다 */}
        <ol className="ob-dots" aria-label={`${STEPS.length}걸음 중 ${at + 1}번째`}>
          {STEPS.map((s, i) => (
            <li key={s.key} className={i === at ? 'is-on' : i < at ? 'is-done' : undefined} />
          ))}
        </ol>

        <div className="ob-body">
          <h2 id="ob-title">{step.title}</h2>
          <p className="ob-text">{step.body}</p>

          {/* 어디로 가면 되는지. 여기서 링크로 보내면 튜토리얼이 중간에 끊겨
              지갑 안내를 못 보고 나간다 — 그래서 자리만 알려 주고 걸음은
              그대로 잇는다(라우트가 없어 되돌아올 수도 없다). */}
          {'where' in step && <p className="ob-where">{step.where}</p>}

          {/* 마지막 걸음에서만 지금까지 마친 것과 남은 것을 보여준다.
              닉네임은 A-02 에서 이미 끝냈으므로 할 일이 아니라 확인이다. */}
          {last && (
            <ul className="ob-checks">
              <li className="is-done">
                <span className="ob-mark" aria-hidden="true">✓</span>
                <b>닉네임</b>
                <span className="ob-sub">{nickname}</span>
              </li>
              <li className={walletLinked ? 'is-done' : undefined}>
                <span className="ob-mark" aria-hidden="true">{walletLinked ? '✓' : '○'}</span>
                <b>지갑 연동</b>
                <span className="ob-sub">
                  {walletLinked ? '연동됨' : '예측 등록에 필요합니다'}
                </span>
              </li>
            </ul>
          )}
        </div>

        <div className="ob-actions">
          {/* 건너뛰기는 늘 남겨 둔다. 로그인 직후에 붙잡아 두지 않는다 */}
          <button type="button" className="ob-skip" onClick={onClose}>
            {last ? '나중에 하기' : '건너뛰기'}
          </button>

          {last ? (
            walletLinked ? (
              /* 연동까지 끝난 회원은 바로 할 일로 보낸다 */
              <Link className="ob-btn solid" to="/stocks" onClick={onClose}>
                종목 둘러보기
              </Link>
            ) : (
              /* M-01 을 여기서 열지 않는다 — 호출부가 이걸 닫고 띄운다 */
              <button type="button" className="ob-btn solid" onClick={onLinkWallet}>
                지갑 연동하기
              </button>
            )
          ) : (
            <button type="button" className="ob-btn solid" onClick={next}>
              다음
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

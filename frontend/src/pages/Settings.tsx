/* H-03 환경 설정 · /settings
   담당 스토리 [ANT-FE-SETTINGS]
   설계서 docs/화면설계서.md §3 H · §4 H-03 · §9.2

   설계 제약
   - 설정 항목은 notifyVerdict · notifySeason · notifySocial · chartView 4개뿐이다.
     임의 토글을 늘리지 않는다. AI 힌트·난이도 설정은 두지 않는다.
   - 닉네임은 중복 확인 API 를 거친다(useNicknameCheck 가 A-02 와 공유).
   - M-09 온보딩은 최초 1회만 뜬다. 건너뛴 사용자를 위한 보완 안내를 이 화면에 둔다.

   백엔드 준비 상태 — 이 화면은 절반이 잠겨 있다
   - PATCH /users/me 는 지금 닉네임만 받는다(UserController 주석 명시).
     그래서 소개·관심 섹터·외부 채널은 읽기 전용으로 둔다. 편집 UI 를 먼저 만들면
     저장 못 하는 입력란이 생기고, 사용자는 저장했다고 믿는다.
   - GET·PUT /users/me/settings 는 아직 없다. 알림 3종과 차트 뷰는 잠긴 카드로 둔다.
     토글을 그려 두고 동작만 막으면 고장 난 화면으로 읽힌다. */
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useApiQuery } from '../api/useApiQuery'
import { NICKNAME_MAX, useNicknameCheck, validateNickname } from '../api/useNicknameCheck'
import { platformLabel, type MyProfile } from '../api/account'
import { ERROR_CODE } from '../api/errors'
import type { MeResponse } from '../auth/session'
import { useAuth } from '../auth/context'
import ErrorState from '../components/state/ErrorState'
import LockedCard from '../components/state/LockedCard'
import ChannelFeeSection from '../components/settings/ChannelFeeSection'
import WalletLinkModal from '../components/wallet/WalletLinkModal'
import '../styles/screens/settings.css'

/* 명세 §회원 의 설정 4개. 서버가 열리면 이 표가 토글 목록이 된다.
   지금은 무엇이 준비 중인지 알려 주는 데만 쓴다. */
const SETTING_ITEMS = [
  { key: 'notifyVerdict', label: '판정 결과 알림', desc: '내 예측이 판정되면 알려 줍니다' },
  { key: 'notifySeason', label: '시즌 진행 · 보상 알림', desc: '시즌 전환과 보상 지급을 알려 줍니다' },
  { key: 'notifySocial', label: '구독 · 구독료 변경 알림', desc: '구독 갱신과 구독료 변경을 알려 줍니다' },
] as const

export default function Settings() {
  const navigate = useNavigate()
  const { user, signOut, setNickname } = useAuth()

  const profile = useApiQuery<MyProfile>('/users/me')

  /* 셸이 들고 있는 닉네임을 기준으로 삼는다 — 입력을 열자마자 서버 응답을
     기다리지 않아도 되고, 변경 직후에도 값이 어긋나지 않는다. */
  const current = user?.nickname ?? ''
  const [value, setValue] = useState(current)
  const { check, markTaken } = useNicknameCheck(value, current)

  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // M-09 를 건너뛴 사용자를 위한 보완 진입로(설계 제약)
  const [linking, setLinking] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    const reason = validateNickname(value)
    if (reason) {
      setError(reason)
      return
    }

    setSaving(true)
    setError(null)
    setSaved(false)
    try {
      const me = await api.patch<MeResponse>('/users/me', { nickname: value })
      // 서버가 확정한 값으로 셸을 갱신한다. 화면 입력값을 그대로 믿지 않는다.
      setNickname(me.nickname ?? value)
      setSaved(true)
      profile.reload()
    } catch (e) {
      /* 중복 검사와 확정 사이에 누가 먼저 가져갈 수 있다. 최종 판정은 서버다. */
      if (e instanceof ApiError && e.code === ERROR_CODE.DUPLICATE_NICKNAME) {
        markTaken(value)
        setError('이미 사용 중인 닉네임입니다.')
      } else {
        setError('닉네임을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      setSaving(false)
    }
  }

  const hint = {
    idle: `${NICKNAME_MAX}자까지 입력할 수 있습니다.`,
    unchanged: '현재 사용 중인 닉네임입니다.',
    checking: '확인 중…',
    available: '사용할 수 있는 닉네임입니다.',
    taken: '이미 사용 중인 닉네임입니다.',
    invalid: check.state === 'invalid' ? check.reason : '',
  }[check.state]

  const interests = profile.data?.interests ?? []
  const channels = profile.data?.channels ?? []
  const walletLinked = user?.walletLinked ?? false

  return (
    <main className="main">
      <div className="main-inner">
        <div className="page-head">
          <h1>환경 설정</h1>
          <p>프로필과 알림, 로그아웃을 관리합니다</p>
        </div>

        {/* ── 온보딩 보완 ───────────────────────────────
            M-09 는 최초 1회만 뜨고 isNew 는 다시 참이 되지 않는다.
            건너뛴 사용자가 남은 단계를 마칠 수 있는 자리가 여기다. */}
        {(current === '' || !walletLinked) && (
          <section className="st-onboarding">
            <h2>아직 마치지 않은 설정</h2>
            <ul>
              {current === '' && (
                <li>
                  <b>닉네임</b>
                  <span>예측 기록과 랭킹에 표시됩니다. 아래에서 정할 수 있습니다.</span>
                </li>
              )}
              {!walletLinked && (
                <li>
                  <b>지갑 연동</b>
                  <span>연동해야 예측을 등록하고 토큰을 받을 수 있습니다.</span>
                  <button type="button" className="st-btn solid" onClick={() => setLinking(true)}>
                    지갑 연동하기
                  </button>
                </li>
              )}
            </ul>
          </section>
        )}

        {/* ── 프로필 ────────────────────────────────── */}
        <section className="st-block">
          <h2>프로필</h2>

          <form className="st-field" onSubmit={submit}>
            <label className="st-label" htmlFor="settings-nickname">닉네임</label>
            <div className="st-row">
              <input
                className="st-input"
                id="settings-nickname"
                type="text"
                value={value}
                onChange={(e) => { setValue(e.target.value); setError(null); setSaved(false) }}
                maxLength={NICKNAME_MAX}
                aria-describedby="settings-nickname-hint"
                aria-invalid={check.state === 'taken' || check.state === 'invalid'}
              />
              <button
                type="submit" className="st-btn solid"
                disabled={saving || check.state !== 'available'}
              >
                {saving ? '저장 중…' : '변경'}
              </button>
            </div>
            <p className={`st-hint is-${check.state}`} id="settings-nickname-hint" aria-live="polite">
              {error ?? (saved ? '닉네임을 변경했습니다.' : hint)}
            </p>
          </form>

          {profile.error ? (
            <ErrorState error={profile.error} onRetry={profile.reload} inline />
          ) : (
            <>
              {/* PATCH /users/me 가 아직 받지 않는 필드들. 읽기 전용으로 둔다 */}
              <div className="st-field">
                <span className="st-label">소개</span>
                <p className="st-readonly">
                  {profile.data?.introduce || '소개가 아직 없습니다'}
                </p>
              </div>

              <div className="st-field">
                <span className="st-label">관심 섹터</span>
                {interests.length > 0 ? (
                  <ul className="st-tags">
                    {interests.map((s) => <li key={s}>{s}</li>)}
                  </ul>
                ) : (
                  <p className="st-readonly">등록된 관심 섹터가 없습니다</p>
                )}
              </div>

              <div className="st-field">
                <span className="st-label">외부 채널</span>
                {channels.length > 0 ? (
                  <ul className="st-channels">
                    {channels.map((c) => (
                      <li key={c.url}>
                        {/* 명세상 https 만 저장된다. 외부로 나가므로 새 탭 + noreferrer */}
                        <a href={c.url} target="_blank" rel="noreferrer noopener">
                          <span className="st-channel-platform">{platformLabel(c.platform)}</span>
                          <span className="st-channel-url">{c.url}</span>
                        </a>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="st-readonly">등록된 채널이 없습니다</p>
                )}
              </div>

              <p className="st-note">
                소개 · 관심 섹터 · 외부 채널 수정은 준비 중입니다. 지금은 닉네임만 변경할 수 있습니다.
              </p>
            </>
          )}
        </section>

        {/* ── 내 채널 ───────────────────────────────────
            원래 E-04 [ANT-FE-CHANNEL-FEE] 의 별도 화면(/me/channel)이었는데
            여기로 합쳤다. 둘 다 "내 계정을 손보는 곳"이라 마이페이지에서
            설정 진입점이 둘로 갈려 있었다.

            섹션 안쪽은 ChannelFeeSection 이 맡는다 — 설계 제약 셋(차기 주기 적용 ·
            FEE_CHANGED 확인 단계 · 변경 이력)도 그 파일 머리말에 있다.
            **아직 목업이다.** GET·PUT /me/channel/fee 가 없다(api/channelFee.ts). */}
        <ChannelFeeSection />

        {/* ── 알림 ─────────────────────────────────── */}
        <section className="st-block">
          <h2>알림</h2>
          {/* 기본 문구는 구독 잠금용이라 title 로 갈아끼운다 — 구독으로 풀리는 잠금이 아니다 */}
          <LockedCard label="알림 설정" title="알림 설정은 준비 중입니다">
            <ul className="st-pending">
              {SETTING_ITEMS.map((s) => (
                <li key={s.key}>
                  <b>{s.label}</b>
                  <span>{s.desc}</span>
                </li>
              ))}
            </ul>
          </LockedCard>
        </section>

        {/* ── 트레이딩 기본 뷰 ──────────────────────── */}
        <section className="st-block">
          <h2>트레이딩 기본 뷰</h2>
          <LockedCard label="차트 뷰 설정" title="차트 뷰 설정은 준비 중입니다">
            <p className="st-pending-note">
              모의투자 차트를 초급(BEGINNER) 또는 고급(ADVANCED) 중 어느 쪽으로 열지 정합니다.
            </p>
          </LockedCard>
        </section>

        {/* ── 로그아웃 ─────────────────────────────── */}
        <section className="st-block">
          <h2>로그아웃</h2>
          <p className="st-note">
            이 브라우저에서 세션을 종료합니다. 다시 이용하려면 로그인해야 합니다.
          </p>
          <button
            type="button" className="st-btn danger"
            onClick={() => { signOut(); navigate('/login', { replace: true }) }}
          >
            로그아웃
          </button>
        </section>

        {linking && (
          <WalletLinkModal
            onClose={() => setLinking(false)}
            // 연동되면 셸의 walletLinked 가 서고, 위 보완 안내가 사라진다
            onLinked={() => profile.reload()}
          />
        )}
      </div>
    </main>
  )
}

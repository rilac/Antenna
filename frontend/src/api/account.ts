/* 계정 도메인. API 명세서 §회원.

   GET /users/me         { id, nickname, introduce, interests, channels, walletAddress }
   GET /users/me/badges  { items: [{ badgeCode, seasonId, pinned, createdAt }] } */

export type ChannelPlatform = 'YOUTUBE' | 'X' | 'BLOG' | 'ETC'

export type UserChannel = {
  platform: ChannelPlatform
  url: string
}

export type MyProfile = {
  id: number
  nickname: string
  introduce: string | null
  /** 관심 섹터. 배열 전체 교체 방식이라 부분 수정이 없다 */
  interests: string[]
  channels: UserChannel[]
  /** 미연동이면 null. 연동 상태는 GET /wallet 이 정본이다 */
  walletAddress: string | null
}

/* 배지는 커서 페이징이 아니다 — { items } 로만 감싸 온다.
   조건 달성 시 서버가 부여하며, 획득분만 내려온다(미획득 목록은 없다). */
export type BadgeList = {
  items: Badge[]
}

export type Badge = {
  badgeCode: string
  /** 시즌에서 딴 배지면 채워진다 */
  seasonId: number | null
  /** 전시 pin 여부 */
  pinned: boolean
  createdAt: string
}

/* badgeCode 는 서버가 늘려 가는 값이다. 표에 없는 코드가 와도 화면이 비지 않도록
   라벨을 못 찾으면 코드를 그대로 보여 준다. */
const BADGE_LABEL: Record<string, string> = {
  CRISIS_SURVIVOR: '위기 극복',
  LONG_TERM_INVESTOR: '장기 투자자',
  PROFIT_ACHIEVED: '수익 달성',
  WINNING_STREAK: '연승',
}

export function badgeLabel(code: string) {
  return BADGE_LABEL[code] ?? code
}

export const PLATFORM_LABEL: Record<ChannelPlatform, string> = {
  YOUTUBE: '유튜브',
  X: 'X',
  BLOG: '블로그',
  ETC: '기타',
}

export function platformLabel(platform: string) {
  return PLATFORM_LABEL[platform as ChannelPlatform] ?? platform
}

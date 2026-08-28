export default function MyPage() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner mypage"}>

          <header className={"mp-head"}>
            <div>
              <h1>마이페이지</h1>
              <p>내 채널과 등록한 예측을 한곳에서 관리합니다.</p>
            </div>
            <a className={"mp-settings"} href={"#"}>
              <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"12"} cy={"12"} r={"3"} /><path d={"M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1v.2a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-2.7-1.2l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H3.4a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.7 6.3l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 2.7-1.1V2a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.2l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7h.2a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1"} /></svg>
              채널 설정
            </a>
          </header>

          {/* 내 채널 */}
          <section className={"mp-card mp-channel"}>
            <div className={"mp-banner"} aria-hidden={"true"}></div>
            <div className={"mp-channel-body"}>
              <span className={"mp-avatar"}>
                <i>안</i>
                <span className={"mp-verified"}><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"3.6"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></span>
              </span>
              <div className={"mp-who"}>
                <h2>안테나님 <span className={"mp-handle"}>@antena</span> <span className={"mp-tier"}>플래티넘 III</span></h2>
                <p>실적 · 수급 기반 중기 성장주 Forecast형 · 반도체 · IT 중심</p>
              </div>
              <div className={"mp-channel-actions"}>
                <a className={"mp-btn solid"} href={"/market"}>
                  <svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"} /><circle cx={"12"} cy={"12"} r={"3"} /></svg>
                  채널 공개 페이지 보기
                </a>
                <a className={"mp-btn ghost"} href={"#"}>
                  <svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M4 20h4L20 8l-4-4L4 16z"} /><path d={"m14 6 4 4"} /></svg>
                  프로필 수정
                </a>
              </div>
            </div>

            <dl className={"mp-stats"}>
              <div><dt>신뢰도</dt><dd>82.4</dd></div>
              <div><dt>적중률</dt><dd>66.0<small>%</small></dd></div>
              <div><dt>구독자</dt><dd>1,248<small>명</small></dd></div>
              <div><dt>등록한 예측</dt><dd>53<small>건</small></dd></div>
            </dl>
          </section>

          {/* 활동 요약 */}
          <dl className={"mp-activity"}>
            <a className={"mp-card mp-act"} href={"/reports"}>
              <i className={"mp-tone-v"}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M6 2h8l4 4v16H6z"} /><path d={"M14 2v4h4"} /><path d={"M9 12h6M9 16h6"} /></svg></i>
              <div><dt>내가 쓴 리포트</dt><dd>3<small>건</small></dd></div>
              <span className={"mp-act-go"} aria-hidden={"true"}>›</span>
            </a>
            <a className={"mp-card mp-act"} href={"/community"}>
              <i className={"mp-tone-b"}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg></i>
              <div><dt>내가 쓴 글</dt><dd>2<small>건</small></dd></div>
              <span className={"mp-act-go"} aria-hidden={"true"}>›</span>
            </a>
            <a className={"mp-card mp-act"} href={"/portfolio"}>
              <i className={"mp-tone-g"}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 17l6-6 4 4 8-8"} /><path d={"M15 7h6v6"} /></svg></i>
              <div><dt>AI Forecast Portfolio</dt><dd>82<small>/100</small></dd></div>
              <span className={"mp-act-go"} aria-hidden={"true"}>›</span>
            </a>
          </dl>

          {/* 내가 등록한 예측 */}
          <section className={"mp-card mp-forecasts"}>
            <div className={"mp-fc-head"}>
              <h2>내가 등록한 예측</h2>
              <i className={"mp-info"} title={"블록체인에 앵커된 예측 기록"}>i</i>
              <div className={"mp-sorts"} role={"group"} aria-label={"정렬"}>
                <button className={"on"} type={"button"} data-sort={"date"}>최신순</button>
                <button type={"button"} data-sort={"result"}>성과순</button>
              </div>
            </div>

            <div className={"mp-scopes"} role={"group"} aria-label={"상태"}>
              <button className={"on"} type={"button"} data-scope={"all"}>전체 <b>8</b></button>
              <button type={"button"} data-scope={"live"}>진행 중 <b>2</b></button>
              <button type={"button"} data-scope={"hit"}>적중 <b>4</b></button>
              <button type={"button"} data-scope={"miss"}>미적중 <b>2</b></button>
            </div>

            <div className={"mp-table-wrap"}>
              <table className={"mp-table"}>
                <thead>
                  <tr><th>종목</th><th>방향</th><th>기간</th><th>목표가</th><th>등록일</th><th>상태</th><th>결과</th><th>액션</th></tr>
                </thead>
                <tbody id={"mp-rows"}>

                  <tr data-scope={"live"} data-date={"20260826"} data-result={"0"} data-tx={"0x9f3a7c21b845de0f6a11c33e7f92b0d48e5c17aa93b6f0e2d418c7a55be03f21"} data-block={"24,881,032"} data-anchored={"2026.08.26 09:14:22"} data-conf={"78%"} data-status={"wait"}>
                    <td><span className={"mp-stock"}><i style={{ background: 'linear-gradient(145deg,#1887f1,#0b5ed7)' }}>삼</i><span><b>삼성전자</b><small>005930</small></span></span></td>
                    <td><span className={"mp-dir up"}>UP</span></td>
                    <td className={"num"}>20영업일</td><td className={"num"}>85,000원</td><td className={"num"}>2026.08.26</td>
                    <td><span className={"mp-state live"}>진행 중 D-14</span></td>
                    <td><span className={"mp-conf"}>확신도 78%</span></td>
                    <td><span className={"mp-row-actions"}><a className={"mp-act-btn"} href={"/stock-detail?code=005930&name=%EC%82%BC%EC%84%B1%EC%A0%84%EC%9E%90&market=KOSPI&sector=%EB%B0%98%EB%8F%84%EC%B2%B4&price=71800&change=1.42&per=14.3&pbr=1.31&logo=logo-samsung&mark=%EC%82%BC"}>상세</a><button className={"mp-act-btn chain"} type={"button"} data-chain aria-expanded={"false"}>원장</button></span></td>
                  </tr>

                  <tr data-scope={"live"} data-date={"20260824"} data-result={"0"} data-tx={"0x41d8b09e7f2a635c18ee40b7d3915ca6207f8b41e9c05d3a7726be18f4c0a9d3"} data-block={"24,842,517"} data-anchored={"2026.08.24 10:02:41"} data-conf={"82%"} data-status={"wait"}>
                    <td><span className={"mp-stock"}><i style={{ background: 'linear-gradient(145deg,#404dff,#3b35d7)' }}>S</i><span><b>SK하이닉스</b><small>000660</small></span></span></td>
                    <td><span className={"mp-dir up"}>UP</span></td>
                    <td className={"num"}>10영업일</td><td className={"num"}>238,000원</td><td className={"num"}>2026.08.24</td>
                    <td><span className={"mp-state live"}>진행 중 D-6</span></td>
                    <td><span className={"mp-conf"}>확신도 82%</span></td>
                    <td><span className={"mp-row-actions"}><a className={"mp-act-btn"} href={"/stock-detail?code=000660&name=SK%ED%95%98%EC%9D%B4%EB%8B%89%EC%8A%A4&market=KOSPI&sector=%EB%B0%98%EB%8F%84%EC%B2%B4&price=198500&change=2.85&per=9.8&pbr=1.72&logo=logo-skhy&mark=S"}>상세</a><button className={"mp-act-btn chain"} type={"button"} data-chain aria-expanded={"false"}>원장</button></span></td>
                  </tr>

                  <tr data-scope={"hit"} data-date={"20260818"} data-result={"4.2"} data-tx={"0x7b25ce8140af396d2e77b0c5194da638f0e2a7c94b18d5306fa7c21e88b40d97"} data-block={"24,703,884"} data-anchored={"2026.08.18 13:31:08"} data-conf={"71%"} data-status={"done"} data-settle={"0xc4e1a97530bd28f6417ae0c93b1d75620f8a3e14d9c07b52e6318fa07c2b9d41"}>
                    <td><span className={"mp-stock"}><i style={{ background: 'linear-gradient(145deg,#12c883,#00a86b)' }}>N</i><span><b>NAVER</b><small>035420</small></span></span></td>
                    <td><span className={"mp-dir down"}>DOWN</span></td>
                    <td className={"num"}>10영업일</td><td className={"num"}>168,000원</td><td className={"num"}>2026.08.18</td>
                    <td><span className={"mp-state hit"}>적중</span></td>
                    <td><span className={"mp-result rise"}>+4.2%</span></td>
                    <td><span className={"mp-row-actions"}><a className={"mp-act-btn"} href={"/stock-detail?code=035420&name=NAVER&market=KOSPI&sector=%EC%9D%B8%ED%84%B0%EB%84%B7&price=176300&change=-0.62&per=18.7&pbr=1.12&logo=logo-naver2&mark=N"}>상세</a><button className={"mp-act-btn chain"} type={"button"} data-chain aria-expanded={"false"}>원장</button></span></td>
                  </tr>

                  <tr data-scope={"hit"} data-date={"20260805"} data-result={"7.9"} data-tx={"0x2ae6f31c90b47d85026ce1a7f4938b06d5c72e10a83f9b4162de07c39a1b58f0"} data-block={"24,381,209"} data-anchored={"2026.08.05 09:47:55"} data-conf={"69%"} data-status={"done"} data-settle={"0x86bd0e4a2f7139c50ae8b217d43f6905c1e72a8b0d94f36e5721ca08bf13d927"}>
                    <td><span className={"mp-stock"}><i style={{ background: 'linear-gradient(145deg,#13aaa2,#087a7b)' }}>현</i><span><b>현대차</b><small>005380</small></span></span></td>
                    <td><span className={"mp-dir up"}>UP</span></td>
                    <td className={"num"}>20영업일</td><td className={"num"}>262,000원</td><td className={"num"}>2026.08.05</td>
                    <td><span className={"mp-state hit"}>적중</span></td>
                    <td><span className={"mp-result rise"}>+7.9%</span></td>
                    <td><span className={"mp-row-actions"}><a className={"mp-act-btn"} href={"/stock-detail?code=005380&name=%ED%98%84%EB%8C%80%EC%B0%A8&market=KOSPI&sector=%EC%9E%90%EB%8F%99%EC%B0%A8&price=242000&change=0.83&per=5.4&pbr=0.68&logo=logo-hyundai&mark=%ED%98%84"}>상세</a><button className={"mp-act-btn chain"} type={"button"} data-chain aria-expanded={"false"}>원장</button></span></td>
                  </tr>

                  <tr data-scope={"miss"} data-date={"20260728"} data-result={"-5.3"} data-tx={"0x5c19a7e2438bf0d61ae9c30f782b45d07e6a1c93f04b8d25e731a06cf9b28d14"} data-block={"24,190,663"} data-anchored={"2026.07.28 11:20:36"} data-conf={"64%"} data-status={"done"} data-settle={"0x3f7ac1e08b529d64107fe3a29c8b0d571e46a2f93b07c815de2409fa6cb31d78"}>
                    <td><span className={"mp-stock"}><i style={{ background: 'linear-gradient(145deg,#c02e67,#981743)' }}>L</i><span><b>LG에너지솔루션</b><small>373220</small></span></span></td>
                    <td><span className={"mp-dir up"}>UP</span></td>
                    <td className={"num"}>10영업일</td><td className={"num"}>372,000원</td><td className={"num"}>2026.07.28</td>
                    <td><span className={"mp-state miss"}>미적중</span></td>
                    <td><span className={"mp-result fall"}>-5.3%</span></td>
                    <td><span className={"mp-row-actions"}><a className={"mp-act-btn"} href={"/stock-detail?code=373220&name=LG%EC%97%90%EB%84%88%EC%A7%80%EC%86%94%EB%A3%A8%EC%8C%98&market=KOSPI&sector=2%EC%B0%A8%EC%A0%84%EC%A7%80&price=342000&change=-1.87&per=72.4&pbr=3.61&logo=logo-lgenergy&mark=L"}>상세</a><button className={"mp-act-btn chain"} type={"button"} data-chain aria-expanded={"false"}>원장</button></span></td>
                  </tr>

                  <tr data-scope={"hit"} data-date={"20260721"} data-result={"2.1"} data-tx={"0xb8420fe37a1c95d06e2b81cf740a3956d1e78b02c93af641507ed2a19bc60f38"} data-block={"24,027,415"} data-anchored={"2026.07.21 14:05:12"} data-conf={"58%"} data-status={"done"} data-settle={"0x91e5c02a76bd413f80ae29c7b1d64530fa8e271c93b05d84e6217ca0bf39d182"}>
                    <td><span className={"mp-stock"}><i style={{ background: 'linear-gradient(145deg,#dca617,#af7800)' }}>카</i><span><b>카카오</b><small>035720</small></span></span></td>
                    <td><span className={"mp-dir down"}>DOWN</span></td>
                    <td className={"num"}>5영업일</td><td className={"num"}>40,000원</td><td className={"num"}>2026.07.21</td>
                    <td><span className={"mp-state hit"}>적중</span></td>
                    <td><span className={"mp-result rise"}>+2.1%</span></td>
                    <td><span className={"mp-row-actions"}><a className={"mp-act-btn"} href={"/stock-detail?code=035720&name=%EC%B9%B4%EC%B4%88%EC%98%A4&market=KOSPI&sector=%EC%9D%B8%ED%84%B0%EB%84%B7&price=42150&change=-1.04&per=25.2&pbr=1.08&logo=logo-kakao2&mark=%EC%B9%B4"}>상세</a><button className={"mp-act-btn chain"} type={"button"} data-chain aria-expanded={"false"}>원장</button></span></td>
                  </tr>

                  <tr data-scope={"miss"} data-date={"20260710"} data-result={"-1.8"} data-tx={"0x64af1c893e07b2d5061ae7f39c48b02d75e1a6cf830b9d24e5713ac06fb28d90"} data-block={"23,842,077"} data-anchored={"2026.07.10 09:58:03"} data-conf={"75%"} data-status={"done"} data-settle={"0x2d7be91c4085af36027ce1b8d493f065a1e72c8b0f34d976e5218ca03bf17d62"}>
                    <td><span className={"mp-stock"}><i style={{ background: 'linear-gradient(145deg,#3aa7b9,#238a9b)' }}>셀</i><span><b>셀트리온</b><small>068270</small></span></span></td>
                    <td><span className={"mp-dir up"}>UP</span></td>
                    <td className={"num"}>60영업일</td><td className={"num"}>210,000원</td><td className={"num"}>2026.07.10</td>
                    <td><span className={"mp-state miss"}>미적중</span></td>
                    <td><span className={"mp-result fall"}>-1.8%</span></td>
                    <td><span className={"mp-row-actions"}><a className={"mp-act-btn"} href={"/stock-detail?code=068270&name=%EC%85%80%ED%8A%B8%EB%A6%AC%EC%98%A8&market=KOSPI&sector=%EB%B0%94%EC%9D%B4%EC%98%A4&price=194600&change=-0.28&per=41.9&pbr=2.54&logo=logo-celltrion&mark=%EC%85%80"}>상세</a><button className={"mp-act-btn chain"} type={"button"} data-chain aria-expanded={"false"}>원장</button></span></td>
                  </tr>

                  <tr data-scope={"hit"} data-date={"20260630"} data-result={"6.4"} data-tx={"0xa3f70b18c2e94d5601be7a3f8c02d951e764ab13c08f9d276e35102fa8bc4d19"} data-block={"23,690,548"} data-anchored={"2026.06.30 10:36:47"} data-conf={"66%"} data-status={"done"} data-settle={"0x7e1c840af2935bd6017ae2c39b48f0d562e1a7c930f8b4d25e6317ca09bf28d4"}>
                    <td><span className={"mp-stock"}><i style={{ background: 'linear-gradient(145deg,#dca617,#af7800)' }}>K</i><span><b>KB금융</b><small>105560</small></span></span></td>
                    <td><span className={"mp-dir up"}>UP</span></td>
                    <td className={"num"}>20영업일</td><td className={"num"}>95,000원</td><td className={"num"}>2026.06.30</td>
                    <td><span className={"mp-state hit"}>적중</span></td>
                    <td><span className={"mp-result rise"}>+6.4%</span></td>
                    <td><span className={"mp-row-actions"}><a className={"mp-act-btn"} href={"/stock-detail?code=105560&name=KB%EA%B8%88%EC%9C%B5&market=KOSPI&sector=%EA%B8%88%EC%9C%B5&price=87900&change=0.11&per=6.2&pbr=0.59&logo=logo-kb&mark=K"}>상세</a><button className={"mp-act-btn chain"} type={"button"} data-chain aria-expanded={"false"}>원장</button></span></td>
                  </tr>

                </tbody>
              </table>
            </div>

            <p className={"mp-empty"} id={"mp-empty"} hidden>조건에 맞는 예측이 없습니다.</p>
            <button className={"mp-more"} id={"mp-more"} type={"button"}>더 보기 (전체 53건) ↓</button>
          </section>

        </div>
      </main>
    </>
  )
}

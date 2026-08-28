export default function Login() {
  return (
    <>
      <div className={"auth"}>
        <main className={"auth-shell"}>
          <section className={"auth-intro"} aria-label={"서비스 소개"}>
            <a className={"intro-brand"} href={"/"}>
              <svg className={"mark"} viewBox={"0 0 40 40"} fill={"none"} aria-hidden={"true"}>
                <rect width={"40"} height={"40"} rx={"11"} fill={"currentColor"} />
                <path d={"M11.5 29.5 20 11.5l8.5 18"} stroke={"#fff"} strokeWidth={"4.2"} strokeLinecap={"round"} strokeLinejoin={"round"} />
                <path d={"M15.8 24.2h8.4"} stroke={"#fff"} strokeWidth={"4.2"} strokeLinecap={"round"} />
              </svg>
              <span className={"name"}>ANTENA</span>
              <span className={"sep"}></span>
              <span className={"tag"}>AI 기반 예측 투자 플랫폼</span>
            </a>

            <div className={"intro-body"}>
              <div className={"intro-copy"}>
                <p className={"eyebrow"}>ANTENA</p>
                <h1>근거를 보고<br />예측하는 투자 플랫폼</h1>
                <p className={"lead"}>AI 리서치와 블록체인 검증, 예측 포트폴리오로<br />더 똑똑한 투자 결정을 경험하세요.</p>

                <ul className={"feats"}>
                  <li className={"feat"}>
                    <span className={"feat-ic"}>
                      <svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.6"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                        <path d={"M12 5a2.5 2.5 0 0 0-5 0 2.5 2.5 0 0 0-2 4 2.5 2.5 0 0 0 1 4.6A2.5 2.5 0 0 0 9 19a2.5 2.5 0 0 0 3-1.5z"} />
                        <path d={"M12 5a2.5 2.5 0 0 1 5 0 2.5 2.5 0 0 1 2 4 2.5 2.5 0 0 1-1 4.6A2.5 2.5 0 0 1 15 19a2.5 2.5 0 0 1-3-1.5z"} />
                        <path d={"M12 5v14"} />
                      </svg>
                    </span>
                    <div>
                      <h3>AI 리서치</h3>
                      <p>대학 데이터와 AI 분석으로 시장 트렌드와<br />유망 인사이트를 제공합니다.</p>
                    </div>
                  </li>
                  <li className={"feat"}>
                    <span className={"feat-ic"}>
                      <svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.6"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                        <path d={"M12 3 4.5 6v6c0 4.5 3.2 7.9 7.5 9 4.3-1.1 7.5-4.5 7.5-9V6z"} />
                        <path d={"m9 12 2.2 2.2L15.5 10"} />
                      </svg>
                    </span>
                    <div>
                      <h3>블록체인 검증</h3>
                      <p>모든 예측과 리서치는 블록체인에 기록되어<br />투명성과 신뢰를 보장합니다.</p>
                    </div>
                  </li>
                  <li className={"feat"}>
                    <span className={"feat-ic"}>
                      <svg width={"22"} height={"22"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.6"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                        <path d={"M3 20h18"} /><path d={"M6 20v-6M10.5 20v-9M15 20v-4"} />
                        <path d={"m13 8 4-4M17 4h-3.4M17 4v3.4"} /><path d={"M19.5 20V8"} />
                      </svg>
                    </span>
                    <div>
                      <h3>예측 포트폴리오</h3>
                      <p>검증된 예측과 자산군의 상관 분석으로<br />포트폴리오를 구성하고 관리하세요.</p>
                    </div>
                  </li>
                </ul>
              </div>

              <figure className={"intro-art"}>
                <img src={"/assets/hero-mascot-v3.png"} alt={"시장 데이터를 가리키는 ANTENA 캐릭터"} />
                <span className={"character-glow glow-one"}></span>
                <span className={"character-glow glow-two"}></span>
              </figure>
            </div>
          </section>

          <section className={"sso"}>
            <a className={"auth-back"} href={"/"}>← 홈으로</a>
            <h2>SSO 로그인</h2>
            <p className={"sub"}>계정을 선택하여 간편하게 로그인하세요.</p>

            <a className={"sso-btn ssafy"} href={"/"}>
              <svg width={"26"} height={"26"} viewBox={"0 0 40 40"} fill={"none"} aria-hidden={"true"}>
                <path d={"M11.5 29.5 20 11.5l8.5 18"} stroke={"#fff"} strokeWidth={"4.2"} strokeLinecap={"round"} strokeLinejoin={"round"} />
                <path d={"M15.8 24.2h8.4"} stroke={"#fff"} strokeWidth={"4.2"} strokeLinecap={"round"} />
              </svg>
              SSAFY로 로그인
            </a>

            <a className={"sso-btn google"} href={"/"}>
              <svg width={"24"} height={"24"} viewBox={"0 0 48 48"} aria-hidden={"true"}>
                <path fill={"#EA4335"} d={"M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"} />
                <path fill={"#4285F4"} d={"M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"} />
                <path fill={"#FBBC05"} d={"M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"} />
                <path fill={"#34A853"} d={"M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"} />
              </svg>
              Google로 로그인
            </a>

            <p className={"sso-foot"}>
              로그인 시 <a href={"#"}>서비스 이용약관</a>과 <a href={"#"}>개인정보 처리방침</a>에 동의하게 됩니다.<br />
              최초 로그인 시 계정이 자동 생성되며, 온보딩으로 이동합니다.
            </p>
          </section>
        </main>
      </div>
    </>
  )
}

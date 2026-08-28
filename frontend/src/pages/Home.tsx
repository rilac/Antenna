import Carousel from "../components/Carousel"

export default function Home() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner home-dashboard"}>
          <section className={"home-card hero-card"} aria-labelledby={"hero-title"}>
            <div className={"hero-copy"}>
              <p className={"hero-welcome"}>안테나님, 환영합니다! <span aria-hidden={"true"}>👋</span></p>
              <h1 id={"hero-title"}>AI가 시장을 분석하고,<br />더 나은 투자 결정을 도와드려요.</h1>
              <p className={"hero-description"}>실시간 데이터와 AI 인사이트로<br />투자의 방향을 제시합니다.</p>
              <a className={"home-primary-button"} href={"/prediction/create"}>예측 시작하기 <span aria-hidden={"true"}>→</span></a>
            </div>
            <img className={"hero-art"} src={"/assets/hero-mascot-v3.png"} alt={"홀로그램 시장 차트를 설명하는 ANTENA 마스코트"} />
          </section>

          <Carousel as="section" className="home-card promotion-card" aria-label="프로모션" autoplay={6000} arrows={false}>
            <article className={"promo-slide"}>
              <div className={"promotion-copy"}>
                <span className={"sponsored-pill"}>스폰서드</span>
                <h2 id={"promotion-title"}>내 예측을 더 많은 사람에게!</h2>
                <p>ANT로 홍보하고 성과를 키우세요.</p>
                <a className={"promotion-button"} href={"/portfolio"}>지금 홍보하기 <span aria-hidden={"true"}>→</span></a>
              </div>
              <img className={"promotion-art"} src={"/assets/promo-mascot-v2.png"} alt={"확성기로 예측을 홍보하는 ANTENA 마스코트"} />
            </article>

            <article className={"promo-slide"}>
              <div className={"promotion-copy"}>
                <span className={"sponsored-pill"}>시즌 2</span>
                <h2>실전 수익률 대회 진행 중!</h2>
                <p>4,328명과 수익률로 겨루고 보상을 받으세요.</p>
                <a className={"promotion-button"} href={"/sim/contest"}>대회 참여하기 <span aria-hidden={"true"}>→</span></a>
              </div>
              <img className={"promotion-art promo-art-right"} src={"/assets/antena_character.png"} alt={""} />
            </article>

            <article className={"promo-slide"}>
              <div className={"promotion-copy"}>
                <span className={"sponsored-pill"}>ON-CHAIN</span>
                <h2>모든 예측은 블록체인에 봉인</h2>
                <p>등록 후 수정할 수 없고, 누구나 검증할 수 있어요.</p>
                <a className={"promotion-button"} href={"/mypage"}>내 예측 원장 보기 <span aria-hidden={"true"}>→</span></a>
              </div>
              <img className={"promotion-art promo-art-right"} src={"/assets/onchain-blocks-transparent-v1.png"} alt={""} />
            </article>
          </Carousel>

          <section className={"home-card market-overview"} aria-labelledby={"market-overview-title"}>
            <div className={"home-card-head"}><h2 id={"market-overview-title"}>시장 Overview</h2><a href={"/stocks"}>더보기 <span aria-hidden={"true"}>›</span></a></div>
            <div className={"market-items"}>
              <article className={"market-item"}><div className={"market-values"}><p>코스피</p><strong className={"num"}>2,663.33</strong></div><span className={"market-change up num"}>+18.42<br />(+0.70%)</span><svg className={"sparkline up-line"} viewBox={"0 0 150 64"} role={"img"} aria-label={"코스피 상승 차트"}><polyline points={"3,54 14,46 21,28 28,20 35,37 43,16 52,30 60,22 69,26 77,22 85,37 94,31 103,16 111,22 121,9 130,15 147,8"} /></svg></article>
              <article className={"market-item"}><div className={"market-values"}><p>코스닥</p><strong className={"num"}>842.67</strong></div><span className={"market-change up num"}>+6.11<br />(+0.73%)</span><svg className={"sparkline up-line"} viewBox={"0 0 150 64"} role={"img"} aria-label={"코스닥 상승 차트"}><polyline points={"3,52 12,44 20,50 29,35 38,31 47,37 56,23 67,26 75,17 85,25 94,7 103,15 112,20 123,17 132,24 141,16 148,23"} /></svg></article>
              <article className={"market-item market-item-last"}><div className={"market-values"}><p>환율 (USD/KRW)</p><strong className={"num"}>1,363.20</strong></div><span className={"market-change down num"}>-3.40<br />(-0.25%)</span><svg className={"sparkline down-line"} viewBox={"0 0 150 64"} role={"img"} aria-label={"원달러 환율 하락 차트"}><polyline points={"3,14 11,21 19,19 27,28 36,12 45,18 54,9 62,19 70,13 79,32 88,25 97,36 107,30 117,43 128,35 137,46 148,50"} /></svg></article>
            </div>
          </section>

          <section className={"home-card asset-summary"} aria-labelledby={"asset-summary-title"}>
            <h2 id={"asset-summary-title"}>내 자산 현황</h2>
            <div className={"asset-box"}>
              <div className={"asset-primary"}><div><p>보유 ANT</p><strong className={"num"}><span className={"ant-coin"}>?</span> 1,250 <small>ANT</small></strong></div><a href={"#"} className={"charge-button"}>ANT 충전</a></div>
              <div className={"asset-stats"}><div><p>사용 ANT</p><strong className={"num down-arrow"}>↓</strong> <b className={"num"}>320 ANT</b></div><div><p>획득 ANT (30일)</p><strong className={"num up-arrow"}>↑</strong> <b className={"num"}>850 ANT</b></div><div><p>총 가치 (KRW)</p><b className={"num"}>₩1,213,750</b></div></div>
            </div>
          </section>

          <section className={"home-card watchlist-card"} aria-labelledby={"watchlist-title"}>
            <div className={"home-card-head watchlist-head"}><h2 id={"watchlist-title"}><span className={"outline-star"} aria-hidden={"true"}>☆</span> 관심 종목</h2><a href={"/watchlist"}>더보기 <span aria-hidden={"true"}>›</span></a></div>
            <div className={"stock-table"} role={"table"} aria-label={"관심 종목"}>
              <div className={"stock-row stock-header"} role={"row"}><span role={"columnheader"}>종목명</span><span role={"columnheader"}>현재가</span><span role={"columnheader"}>전일 대비</span><span role={"columnheader"}>차트(1D)</span><span></span></div>
              <div className={"stock-row"} role={"row"}><span className={"stock-name"}><i className={"stock-logo samsung"}>SAMSUNG</i><b>삼성전자</b> <small className={"num"}>005930</small></span><strong className={"num"}>78,600 원</strong><strong className={"num up"}>+1,300 (+1.68%)</strong><svg className={"stock-spark up-line"} viewBox={"0 0 100 32"}><polyline points={"2,28 10,15 18,20 27,9 37,14 47,6 57,13 67,8 76,11 86,4 98,2"} /></svg><button aria-label={"삼성전자 관심 종목 해제"}>☆</button></div>
              <div className={"stock-row"} role={"row"}><span className={"stock-name"}><i className={"stock-logo sk"}>SK</i><b>SK하이닉스</b> <small className={"num"}>000660</small></span><strong className={"num"}>188,700 원</strong><strong className={"num up"}>+2,900 (+1.70%)</strong><svg className={"stock-spark up-line"} viewBox={"0 0 100 32"}><polyline points={"2,27 10,16 19,21 28,10 38,15 47,6 57,11 66,7 76,10 87,3 98,6"} /></svg><button aria-label={"SK하이닉스 관심 종목 해제"}>☆</button></div>
              <div className={"stock-row"} role={"row"}><span className={"stock-name"}><i className={"stock-logo naver"}>NAVER</i><b>NAVER</b> <small className={"num"}>035420</small></span><strong className={"num"}>205,000 원</strong><strong className={"num down"}>-1,000 (-0.48%)</strong><svg className={"stock-spark down-line"} viewBox={"0 0 100 32"}><polyline points={"2,4 10,10 18,7 27,18 36,14 46,24 56,19 65,28 74,22 84,29 98,26"} /></svg><button aria-label={"NAVER 관심 종목 해제"}>☆</button></div>
              <div className={"stock-row"} role={"row"}><span className={"stock-name"}><i className={"stock-logo lg"}>LG</i><b>LG에너지솔루션</b> <small className={"num"}>373220</small></span><strong className={"num"}>362,000 원</strong><strong className={"num down"}>-2,500 (-0.69%)</strong><svg className={"stock-spark down-line"} viewBox={"0 0 100 32"}><polyline points={"2,3 11,9 20,8 29,19 39,15 48,26 57,21 67,28 78,24 88,29 98,25"} /></svg><button aria-label={"LG에너지솔루션 관심 종목 해제"}>☆</button></div>
              <div className={"stock-row"} role={"row"}><span className={"stock-name"}><i className={"stock-logo kakao"}>kakao</i><b>카카오</b> <small className={"num"}>035720</small></span><strong className={"num"}>47,950 원</strong><strong className={"num down"}>-350 (-0.72%)</strong><svg className={"stock-spark down-line"} viewBox={"0 0 100 32"}><polyline points={"2,8 11,18 20,12 29,26 38,18 48,29 57,21 68,27 78,23 89,30 98,25"} /></svg><button aria-label={"카카오 관심 종목 해제"}>☆</button></div>
            </div>
            <a className={"manage-watchlist"} href={"/watchlist"}><span aria-hidden={"true"}>⚙</span> 관심 종목 관리</a>
          </section>

          <section className={"home-card predictor-card"} aria-labelledby={"predictor-title"}>
            <div className={"predictor-heading"}><h2 id={"predictor-title"}><span aria-hidden={"true"}>💡</span> 주목할 예측가 <small>· Sponsored</small></h2><a href={"/market"}>스폰서드 더보기 <span aria-hidden={"true"}>›</span></a></div>
            <div className={"predictor-profile"}><div className={"analyst-avatar"} role={"img"} aria-label={"반도체훈련소 예측가 프로필"}><span className={"hair"}></span><span className={"face"}></span><span className={"body"}></span></div><div className={"analyst-copy"}><h3>반도체훈련소 <span className={"verified"} aria-label={"인증됨"}>✓</span></h3><p>반도체 사이클과 HBM 전망 분석</p></div></div>
            <div className={"predictor-stats"}><div><p>Forecast</p><strong className={"num"}>128건</strong></div><div><p>적중률</p><strong className={"num"}>78.6%</strong></div><div><p>누적 팔로워</p><strong className={"num"}>12.3K</strong></div></div>
            <div className={"predictor-tags"}><span>반도체 특화</span><span>장기 성장주</span></div>
            <div className={"predictor-footer"}><div className={"social-icons"} aria-label={"활동 채널"}><i className={"youtube"}>▶</i><i className={"instagram"}>◎</i><i className={"blog"}>blog</i></div><div className={"predictor-actions"}><a className={"profile-button"} href={"/community"}>프로필 보기</a><a className={"content-button"} href={"/reports"}>콘텐츠 보기</a></div></div>
          </section>
        </div>
      </main>
    </>
  )
}

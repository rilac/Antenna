import Carousel from "../components/Carousel"

export default function StockDetail() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner stock-detail"}>

          {/* 종목 아이덴티티 + 핵심 지표 */}
          <header className={"sd-head"}>
            <div className={"sd-identity"}>
              <i className={"sd-logo"} id={"sd-logo"}>SAMSUNG</i>
              <div>
                <p className={"sd-title"}>
                  <strong id={"sd-name"}>삼성전자</strong>
                  <span className={"sd-code num"} id={"sd-code"}>005930</span>
                  <button className={"sd-fav"} id={"sd-fav"} type={"button"} aria-pressed={"false"}><i>☆</i> 찜하기</button>
                </p>
                <p className={"sd-price"}><span className={"num"} id={"sd-price"}>78,500</span><span>원</span></p>
                <p className={"sd-delta num rise"} id={"sd-delta"}>+1,300 (+1.68%)</p>
                <p className={"sd-asof"}><b>종가 기준</b> <span className={"num"} id={"sd-date"}>2025.06.02</span></p>
              </div>
            </div>

            <div className={"sd-metrics"}>
              <dl className={"sd-metric-row"}>
                <div className={"sd-metric"}><dt>시가총액 <i className={"sd-info"} title={"발행주식수 × 현재가"}>i</i></dt><dd id={"m-cap"}>469.0조원</dd></div>
                <div className={"sd-metric"}><dt>PER (TTM) <i className={"sd-info"} title={"최근 12개월 순이익 기준 주가수익비율"}>i</i></dt><dd id={"m-per"}>17.21배</dd></div>
                <div className={"sd-metric"}><dt>PBR (TTM) <i className={"sd-info"} title={"주가순자산비율"}>i</i></dt><dd id={"m-pbr"}>1.21배</dd></div>
                <div className={"sd-metric"}><dt>EPS (TTM) <i className={"sd-info"} title={"주당순이익"}>i</i></dt><dd id={"m-eps"}>4,554원</dd></div>
                <div className={"sd-metric"}><dt>ROE (TTM) <i className={"sd-info"} title={"자기자본이익률"}>i</i></dt><dd id={"m-roe"}>7.23%</dd></div>
              </dl>
              <dl className={"sd-metric-row"}>
                <div className={"sd-metric"}><dt>배당수익률 <i className={"sd-info"} title={"주당 배당금 ÷ 현재가"}>i</i></dt><dd id={"m-div"}>2.32%</dd></div>
                <div className={"sd-metric wide"}><dt>52주 범위 <i className={"sd-info"} title={"최근 52주 최저 ~ 최고가"}>i</i></dt><dd id={"m-range"}>56,900 ~ 88,800원</dd></div>
                <div className={"sd-metric"}><dt>업종 평균 PER <i className={"sd-info"} title={"동일 업종 종목의 PER 중앙값"}>i</i></dt><dd id={"m-speer"}>19.42배</dd></div>
                <div className={"sd-metric"}><dt>업종 평균 PBR <i className={"sd-info"} title={"동일 업종 종목의 PBR 중앙값"}>i</i></dt><dd id={"m-spbr"}>1.48배</dd></div>
              </dl>
              <a className={"sd-more"} href={"#"}>더보기 ›</a>
            </div>
          </header>

          {/* AI 브리핑 · 뉴스 */}
          <section className={"sd-insight"}>
            <article className={"sd-card"}>
              <div className={"sd-card-head"}>
                <span className={"sd-badge"}>
                  <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                    <path d={"M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M18 6l-2.5 2.5M8.5 15.5 6 18"} />
                  </svg>
                </span>
                <h2>AI 한줄 브리핑</h2>
              </div>
              <div className={"sd-brief"}>
                <p id={"sd-brief-text"}>메모리 반도체 업황 회복과 HBM 수요 증가로 실적 개선 기대가 지속되고 있습니다. 스마트폰·가전 등 세트 사업의 안정적인 수요와 메모리 경쟁력 강화가 긍정적으로 작용할 전망입니다.</p>
                <figure aria-hidden={"true"}>
                  <svg viewBox={"0 0 140 140"} fill={"none"}>
                    <path d={"M70 30 100 47v34L70 98 40 81V47z"} fill={"#c9c6f7"} opacity={".45"} />
                    <path d={"M70 44 88 54v20L70 84 52 74V54z"} fill={"#8f8bef"} />
                    <path d={"M70 44 88 54 70 64 52 54z"} fill={"#b3b0f5"} />
                    <path d={"M70 64v20L52 74V54z"} fill={"#7b76e8"} />
                    <g stroke={"#a5a2f2"} strokeWidth={"2.6"} strokeLinecap={"round"}>
                      <path d={"M70 30V16M52 54 38 46M88 54l14-8M70 98v14M52 74l-14 8M88 74l14 8"} />
                    </g>
                    <g fill={"#a5a2f2"}><circle cx={"70"} cy={"14"} r={"4"} /><circle cx={"36"} cy={"45"} r={"4"} /><circle cx={"104"} cy={"45"} r={"4"} /><circle cx={"70"} cy={"114"} r={"4"} /><circle cx={"36"} cy={"83"} r={"4"} /><circle cx={"104"} cy={"83"} r={"4"} /></g>
                    <path d={"M112 22l3 8 8 3-8 3-3 8-3-8-8-3 8-3z"} fill={"#c7c4f8"} />
                    <path d={"M27 100l2.2 6 6 2.2-6 2.2-2.2 6-2.2-6-6-2.2 6-2.2z"} fill={"#d5d3fa"} />
                  </svg>
                </figure>
              </div>
            </article>

            <article className={"sd-card"}>
              <div className={"sd-card-head"}>
                <span className={"sd-badge"}>
                  <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                    <rect x={"3"} y={"4"} width={"18"} height={"16"} rx={"2.5"} /><path d={"M7 9h5M7 13h5M7 16.5h3"} /><path d={"M16 9h1.5M16 13h1.5"} />
                  </svg>
                </span>
                <h2>최근 뉴스 요약</h2>
              </div>
              <ul className={"sd-news"} id={"sd-news"}>
                <li><i></i><span>삼성전자, 차세대 HBM3E 12단 검증 통과 (연합뉴스)</span><time>2025.06.02</time></li>
                <li><i></i><span>글로벌 스마트폰 시장 회복세 뚜렷 (ZDNet)</span><time>2025.06.01</time></li>
                <li><i></i><span>미국 반도체 보조금 최종 가이드라인 발표 (로이터)</span><time>2025.05.30</time></li>
              </ul>
            </article>
          </section>

          {/* 포인트 3종 */}
          <Carousel as="section" className="sd-points">
            <article className={"sd-point good"}>
              <div className={"sd-point-head"}><i>↑</i><h2>긍정 포인트</h2></div>
              <ul id={"pt-good"}>
                <li><em>✓</em>HBM 등 고부가 메모리 수요 확대에 따른 실적 개선 기대</li>
                <li><em>✓</em>글로벌 스마트폰·가전 수요 회복 및 프리미엄 제품 비중 확대</li>
                <li><em>✓</em>파운드리 기술 경쟁력 강화 및 신규 고객사 확보 기대</li>
              </ul>
            </article>
            <article className={"sd-point risk"}>
              <div className={"sd-point-head"}><i>!</i><h2>위험 포인트</h2></div>
              <ul id={"pt-risk"}>
                <li><em>!</em>글로벌 경기 둔화 및 수요 변동성 지속</li>
                <li><em>!</em>경쟁 심화에 따른 메모리 가격 변동성</li>
                <li><em>!</em>미·중 갈등 등 지정학적 리스크 지속</li>
              </ul>
            </article>
            <article className={"sd-point check"}>
              <div className={"sd-point-head"}>
                <i><svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2.6"} strokeLinecap={"round"}><circle cx={"11"} cy={"11"} r={"7"} /><path d={"m20 20-3.6-3.6"} /></svg></i>
                <h2>확인할 포인트</h2>
              </div>
              <ul id={"pt-check"}>
                <li><em>✓</em>메모리 업황 회복 속도 및 가격 추이</li>
                <li><em>✓</em>스마트폰·가전 수요 회복 지속 여부</li>
                <li><em>✓</em>차세대 공정 전환 및 파운드리 수주 성과</li>
              </ul>
            </article>
          </Carousel>

          {/* 예측 등록으로 */}
          <section className={"sd-banner"}>
            <figure className={"sd-banner-art"}>
              <img src={"/assets/predict-banner-art.svg"} alt={""} aria-hidden={"true"} />
            </figure>
            <div className={"sd-banner-copy"}>
              <h2>이 정보로 예측하러 가기</h2>
              <p>핵심 지표와 뉴스 요약을 바탕으로<br />예측을 시작해보세요.</p>
            </div>
            <a className={"sd-cta"} id={"sd-cta"} href={"/prediction/create"}>
              <svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2.2"} strokeLinecap={"round"} strokeLinejoin={"round"}>
                <path d={"M3 17l6-6 4 4 8-8"} /><path d={"M15 7h6v6"} />
              </svg>
              예측하러 가기
              <svg width={"24"} height={"24"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2.2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M5 12h13M13 6l6 6-6 6"} /></svg>
            </a>
            <span className={"sd-banner-bars"} aria-hidden={"true"}>
              <i style={{ height: '52px' }}></i><i style={{ height: '88px' }}></i><i style={{ height: '66px' }}></i><i style={{ height: '112px' }}></i>
            </span>
          </section>

        </div>
      </main>
    </>
  )
}

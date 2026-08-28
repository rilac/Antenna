import Carousel from "../components/Carousel"

export default function Portfolio() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner forecast"}>

          <header className={"fc-head"}>
            <div>
              <h1>AI Forecast Portfolio <span className={"fc-beta"}>BETA</span></h1>
              <p>블록체인에 기록된 나의 예측 데이터를 AI가 분석해 만든 포트폴리오입니다.</p>
            </div>
            <a className={"fc-share"} href={"#"}>
              <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"9"} cy={"8"} r={"3.2"} /><path d={"M3 20v-1.5A4.5 4.5 0 0 1 7.5 14h3a4.5 4.5 0 0 1 4.5 4.5V20"} /><path d={"M17 6.5 21 9l-4 2.5"} /></svg>
              포트폴리오 공유
            </a>
          </header>

          {/* 요약 */}
          <dl className={"fc-kpis"}>
            <div className={"fc-card fc-kpi"}>
              <div><dt>검증 완료 Forecast</dt><dd>47 <span>건</span></dd><small>진행 중 6건<i>|</i>전체 53건</small></div>
              <i className={"tone-v"}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><rect x={"5"} y={"4"} width={"14"} height={"17"} rx={"2.5"} /><path d={"M9 4V2.8h6V4"} /><path d={"m9 12 2 2 4-4"} /></svg></i>
            </div>
            <div className={"fc-card fc-kpi"}>
              <div><dt>적중률 (Hit Rate)</dt><dd>66.0<span>%</span></dd><small>31건 적중 / 47건 완료</small></div>
              <i className={"tone-v"}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"}><circle cx={"12"} cy={"12"} r={"9"} /><circle cx={"12"} cy={"12"} r={"5"} /><circle cx={"12"} cy={"12"} r={"1.7"} fill={"currentColor"} /></svg></i>
            </div>
            <div className={"fc-card fc-kpi"}>
              <div><dt>평균 목표가 오차</dt><dd>6.8<span>%</span></dd><small>낮을수록 정확해요</small></div>
              <i className={"tone-g"}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M3 17l6-6 4 4 8-8"} /><path d={"M15 7h6v6"} /></svg></i>
            </div>
            <div className={"fc-card fc-kpi"}>
              <div><dt>평균 확신도</dt><dd>73<span>%</span></dd><small>나의 평균 확신도</small></div>
              <i className={"tone-v"}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 4a5 5 0 0 0-5 5c0 1.6-1 2.2-1 3.4 0 .9.7 1.4 1.4 1.6v1.5A2.5 2.5 0 0 0 10 18h1v2"} /><path d={"M12 4a5 5 0 0 1 5 5c0 3-2 4-2 6.5V18"} /></svg></i>
            </div>
            <div className={"fc-card fc-kpi"}>
              <div><dt>Reputation 점수</dt><dd>82 <span>/100</span></dd><small>상위 18%</small></div>
              <i className={"tone-b"}><svg width={"26"} height={"26"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.7"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 3 20 7.5v9L12 21l-8-4.5v-9z"} /><path d={"M12 8.5 15.5 10.5v4L12 16.5 8.5 14.5v-4z"} /></svg></i>
            </div>
          </dl>

          {/* 분석 · 추이 */}
          <div className={"fc-two"}>

            <section className={"fc-profile"}>
              <p className={"fc-profile-head"}>AI Forecast Portfolio 분석 <i className={"fc-info"} title={"최근 6개월 예측 데이터 기반"}>i</i></p>
              <div className={"fc-profile-body"}>
                <div>
                  <h2>실적 · 수급 기반 중기 성장주 Forecast형</h2>
                  <p>최근 6개월 동안의 47건의 Forecast를 분석한 결과, 반도체·IT 업종의 10~20영업일 중기 예측에서 강한 성과를 보였습니다. 실적과 수급 데이터를 주요 근거로 활용하며, 기업의 중장기 방향을 예측하는 데 강점이 있습니다.</p>
                  <dl className={"fc-traits"}>
                    <div className={"fc-trait"}><dt>강한 섹터</dt><dd>반도체, IT, 자동차</dd></div>
                    <div className={"fc-trait"}><dt>강한 기간</dt><dd>10 ~ 20 영업일</dd></div>
                    <div className={"fc-trait"}><dt>주요 근거</dt><dd>실적 &gt; 수급 &gt; IR &gt; 뉴스</dd></div>
                    <div className={"fc-trait"}><dt>예측 성향</dt><dd>성장주 · 실적 기반 중기형</dd></div>
                  </dl>
                </div>
                <figure className={"fc-radar"}>
                  <svg id={"fc-radar"} viewBox={"0 0 280 260"} role={"img"} aria-label={"예측 역량 5각 그래프"}></svg>
                </figure>
              </div>
            </section>

            <section className={"fc-card fc-trend"}>
              <div className={"fc-trend-head"}>
                <h2>예측 성과 추이</h2>
                <i className={"fc-info"} title={"주 단위 집계"}>i</i>
                <span className={"fc-ranges"} role={"group"} aria-label={"기간"}>
                  <button type={"button"}>1개월</button>
                  <button type={"button"}>3개월</button>
                  <button className={"on"} type={"button"}>6개월</button>
                  <button type={"button"}>1년</button>
                  <button type={"button"}>전체</button>
                </span>
              </div>

              <div className={"fc-metrics"} role={"group"} aria-label={"지표"}>
                <button className={"on"} type={"button"}>적중률</button>
                <button type={"button"}>누적 수익률</button>
                <button type={"button"}>목표가 오차</button>
              </div>

              <p className={"fc-legend"}>
                <span><i></i>나의 적중률</span>
                <span><i className={"dash"}></i>전체 사용자 평균</span>
              </p>

              <svg id={"fc-trend"} viewBox={"0 0 900 300"} role={"img"} aria-label={"적중률 추이"}></svg>
              <p className={"fc-foot-note"}>* 적중률: 예측한 방향(상승/하락)이 실제와 일치한 비율</p>
            </section>
          </div>

          {/* 4분할 */}
          <Carousel className="fc-four">

            <section className={"fc-card fc-panel"}>
              <div className={"fc-panel-head"}><h2>섹터별 적중률</h2><i className={"fc-info"} title={"완료된 예측 기준"}>i</i><a className={"fc-more"} href={"#"}>더 보기 ›</a></div>
              <ul className={"fc-bars"}>
                <li className={"fc-bar"}><b>반도체</b><span className={"fc-track"}><i className={"fc-fill"} style={{ width: '78%' }}></i></span><em>78%</em><span className={"fc-best"}>BEST</span></li>
                <li className={"fc-bar"}><b>자동차</b><span className={"fc-track"}><i className={"fc-fill"} style={{ width: '67%' }}></i></span><em>67%</em></li>
                <li className={"fc-bar"}><b>2차전지</b><span className={"fc-track"}><i className={"fc-fill"} style={{ width: '56%' }}></i></span><em>56%</em></li>
                <li className={"fc-bar"}><b>인터넷</b><span className={"fc-track"}><i className={"fc-fill"} style={{ width: '71%' }}></i></span><em>71%</em></li>
                <li className={"fc-bar"}><b>바이오</b><span className={"fc-track"}><i className={"fc-fill"} style={{ width: '63%' }}></i></span><em>63%</em></li>
                <li className={"fc-bar"}><b>금융</b><span className={"fc-track"}><i className={"fc-fill"} style={{ width: '52%' }}></i></span><em>52%</em></li>
              </ul>
              <p className={"fc-axis"}><span>0%</span><span>25%</span><span>50%</span><span>75%</span><span>100%</span></p>
            </section>

            <section className={"fc-card fc-panel"}>
              <div className={"fc-panel-head"}><h2>기간별 적중률</h2><i className={"fc-info"} title={"예측 기간별 집계"}>i</i><a className={"fc-more"} href={"#"}>더 보기 ›</a></div>
              <ul className={"fc-bars"}>
                <li className={"fc-bar"}><b>5 영업일</b><span className={"fc-track"}><i className={"fc-fill"} style={{ width: '54%' }}></i></span><em>54%</em></li>
                <li className={"fc-bar"}><b>10 영업일</b><span className={"fc-track"}><i className={"fc-fill"} style={{ width: '68%' }}></i></span><em>68%</em></li>
                <li className={"fc-bar"}><b>20 영업일</b><span className={"fc-track"}><i className={"fc-fill"} style={{ width: '76%' }}></i></span><em>76%</em><span className={"fc-best"}>BEST</span></li>
                <li className={"fc-bar"}><b>60 영업일</b><span className={"fc-track"}><i className={"fc-fill"} style={{ width: '64%' }}></i></span><em>64%</em></li>
                <li className={"fc-bar"}><b>120 영업일</b><span className={"fc-track"}><i className={"fc-fill"} style={{ width: '58%' }}></i></span><em>58%</em></li>
              </ul>
              <p className={"fc-axis"}><span>0%</span><span>25%</span><span>50%</span><span>75%</span><span>100%</span></p>
            </section>

            <section className={"fc-card fc-panel"}>
              <div className={"fc-panel-head"}><h2>근거 사용 패턴</h2><i className={"fc-info"} title={"예측 등록 시 선택한 근거"}>i</i><a className={"fc-more"} href={"#"}>더 보기 ›</a></div>
              <div className={"fc-donut-wrap"}>
                <div className={"fc-donut"}>
                  <svg viewBox={"0 0 42 42"} width={"132"} height={"132"} aria-hidden={"true"}>
                    <circle cx={"21"} cy={"21"} r={"15.9"} fill={"none"} stroke={"#f0f1f7"} strokeWidth={"8"} />
                    <circle cx={"21"} cy={"21"} r={"15.9"} fill={"none"} stroke={"#7b6cf0"} strokeWidth={"8"} strokeDasharray={"34 66"} strokeDashoffset={"0"} />
                    <circle cx={"21"} cy={"21"} r={"15.9"} fill={"none"} stroke={"#4a9cf5"} strokeWidth={"8"} strokeDasharray={"27 73"} strokeDashoffset={"-34"} />
                    <circle cx={"21"} cy={"21"} r={"15.9"} fill={"none"} stroke={"#3ec9a0"} strokeWidth={"8"} strokeDasharray={"21 79"} strokeDashoffset={"-61"} />
                    <circle cx={"21"} cy={"21"} r={"15.9"} fill={"none"} stroke={"#f0a72c"} strokeWidth={"8"} strokeDasharray={"12 88"} strokeDashoffset={"-82"} />
                    <circle cx={"21"} cy={"21"} r={"15.9"} fill={"none"} stroke={"#b7bdcb"} strokeWidth={"8"} strokeDasharray={"6 94"} strokeDashoffset={"-94"} />
                  </svg>
                  <span className={"fc-donut-mid"}><b>총 152회</b><small>근거 사용</small></span>
                </div>
                <ul className={"fc-donut-legend"}>
                  <li><i style={{ background: '#7b6cf0' }}></i><b>DART (공시)</b><em>34%</em></li>
                  <li><i style={{ background: '#4a9cf5' }}></i><b>IR 자료</b><em>27%</em></li>
                  <li><i style={{ background: '#3ec9a0' }}></i><b>NEWS (뉴스)</b><em>21%</em></li>
                  <li><i style={{ background: '#f0a72c' }}></i><b>기술지표</b><em>12%</em></li>
                  <li><i style={{ background: '#b7bdcb' }}></i><b>수급 데이터</b><em>6%</em></li>
                </ul>
              </div>
            </section>

            <section className={"fc-card fc-panel"}>
              <div className={"fc-panel-head"}><h2>확신도 Calibration</h2><i className={"fc-info"} title={"부여한 확신도와 실제 적중률 비교"}>i</i><a className={"fc-more"} href={"#"}>더 보기 ›</a></div>
              <table className={"fc-calib"}>
                <thead><tr><th>확신도 구간</th><th>예측 건수</th><th>실제 적중률</th></tr></thead>
                <tbody>
                  <tr><td>90% 이상</td><td className={"num"}>8건</td><td className={"num fall"}>67%</td></tr>
                  <tr><td>80% ~ 90%</td><td className={"num"}>18건</td><td className={"num rise"} style={{ color: '#16a06a' }}>72%</td></tr>
                  <tr><td>70% ~ 80%</td><td className={"num"}>15건</td><td className={"num"} style={{ color: '#16a06a' }}>69%</td></tr>
                  <tr><td>70% 미만</td><td className={"num"}>6건</td><td className={"num"} style={{ color: '#16a06a' }}>48%</td></tr>
                </tbody>
              </table>
              <p className={"fc-ai-note"}>
                <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M9 18h6M10 21h4"} /><path d={"M12 3a6 6 0 0 0-3.5 10.9c.5.4.8 1 .8 1.6h5.4c0-.6.3-1.2.8-1.6A6 6 0 0 0 12 3z"} /></svg>
                <span><b>AI 분석:</b> 높은 확신도를 부여할수록 정확도가 비례해서 높아지는 경향은 아직 뚜렷하지 않습니다.</span>
              </p>
            </section>
          </Carousel>

          {/* 대표 Forecast · 내 카드 */}
          <div className={"fc-bottom"}>

            <section className={"fc-card fc-panel"}>
              <div className={"fc-panel-head"}><h2>AI가 선정한 대표 Forecast</h2><i className={"fc-info"} title={"성과와 근거 구성을 함께 고려해 선정"}>i</i></div>
              <div className={"fc-picks"}>

                <article className={"fc-pick"}>
                  <span className={"fc-pick-flag good"}>대표 성공 Forecast</span>
                  <p className={"fc-pick-title"}>
                    <b>삼성전자</b><span className={"fc-dir"}>UP</span><span className={"fc-span"}>20영업일</span>
                    <span className={"fc-result up"}>+12.4%</span>
                  </p>
                  <p>반기보고서의 HBM 사업 확대와 IR의 공급 증가 계획을 근거로 상승을 예상했습니다. 결과적으로 기준가 대비 +12.4% 상승하며 방향 예측에 성공했습니다.</p>
                  <div className={"fc-evidence"}>
                    <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M6 2h8l4 4v16H6z"} /><path d={"M14 2v4h4"} /></svg>DART 2</span>
                    <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"}><circle cx={"12"} cy={"12"} r={"8"} /><circle cx={"12"} cy={"12"} r={"3"} /></svg>IR 1</span>
                    <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><rect x={"3"} y={"5"} width={"18"} height={"14"} rx={"2"} /><path d={"M7 9h6M7 13h6"} /></svg>NEWS 1</span>
                    <span className={"chain"}><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2.2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg>블록체인 검증 ✓</span>
                  </div>
                </article>

                <article className={"fc-pick"}>
                  <span className={"fc-pick-flag bad"}>대표 실패 Forecast</span>
                  <p className={"fc-pick-title"}>
                    <b>LG에너지솔루션</b><span className={"fc-dir"}>UP</span><span className={"fc-span"}>10영업일</span>
                    <span className={"fc-result down"}>-5.3%</span>
                  </p>
                  <p>실적 회복에 초점을 맞췄으나, 예측 기간 중 발생한 업황 악화와 외국인 매도세를 충분히 반영하지 못했습니다.</p>
                  <div className={"fc-evidence"}>
                    <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M6 2h8l4 4v16H6z"} /><path d={"M14 2v4h4"} /></svg>DART 1</span>
                    <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"}><circle cx={"12"} cy={"12"} r={"8"} /><circle cx={"12"} cy={"12"} r={"3"} /></svg>IR 1</span>
                    <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><rect x={"3"} y={"5"} width={"18"} height={"14"} rx={"2"} /><path d={"M7 9h6M7 13h6"} /></svg>NEWS 2</span>
                    <span className={"chain"}><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2.2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg>블록체인 검증 ✓</span>
                  </div>
                </article>
              </div>
            </section>

            <section className={"fc-card fc-panel"}>
              <div className={"fc-panel-head"}><h2>내 포트폴리오 카드</h2><i className={"fc-info"} title={"공유용 카드 이미지"}>i</i></div>

              <div className={"fc-mycard"}>
                <div className={"fc-mycard-body"}>
                  <figure className={"fc-mycard-art"}><img src={"/assets/antena_character.png"} alt={""} aria-hidden={"true"} /></figure>
                  <div className={"fc-mycard-main"}>
                    <h3>ANTENA Verified<br />Forecast Portfolio</h3>
                    <p className={"fc-mycard-who"}>안테나님</p>
                    <p className={"fc-mycard-type"}>실적 · 수급 기반 중기 Forecast형</p>
                    <dl className={"fc-mycard-stats"}>
                      <div><dd>47</dd><dt>Forecast</dt></div>
                      <div><dd>66.0%</dd><dt>Hit Rate</dt></div>
                      <div><dd>78%</dd><dt>반도체</dt></div>
                      <div><dd>76%</dd><dt>20D Forecast</dt></div>
                    </dl>
                  </div>
                </div>
                <span className={"fc-chain"}>
                  <svg width={"13"} height={"13"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1.5 1.5"} /><path d={"M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1.5-1.5"} /></svg>
                  Blockchain Verified
                </span>
                <div className={"fc-mycard-actions"}>
                  <a className={"fc-share-btn"} href={"#"}>
                    <svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><circle cx={"18"} cy={"5"} r={"2.6"} /><circle cx={"6"} cy={"12"} r={"2.6"} /><circle cx={"18"} cy={"19"} r={"2.6"} /><path d={"m8.4 10.8 7.2-4.1M8.4 13.2l7.2 4.1"} /></svg>
                    공유하기
                  </a>
                  <a className={"fc-dl-btn"} href={"#"} aria-label={"카드 이미지 저장"}>
                    <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 4v11"} /><path d={"m7 11 5 5 5-5"} /><path d={"M4 19h16"} /></svg>
                  </a>
                </div>
              </div>
            </section>
          </div>

        </div>
      </main>
    </>
  )
}

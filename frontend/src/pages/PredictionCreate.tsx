export default function PredictionCreate() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner prediction-create"}>
          <header className={"prediction-stockbar"}>
            <div className={"prediction-stock-identity"}>
              <i className={"prediction-stock-logo"}>SAMSUNG</i>
              <div>
                <p className={"prediction-stock-name"}><strong>삼성전자</strong> <span className={"num"}>005930</span> <button aria-label={"삼성전자 관심 종목 추가"}>☆</button></p>
                <p className={"prediction-stock-price"}><strong className={"num"}>78,500</strong>원 <b className={"num"}>+1,300 (+1.68%)</b></p>
                <p className={"prediction-stock-meta"}>KRX <span></span> 장마감 06.02 15:30</p>
              </div>
            </div>
            <section className={"evidence-summary"} aria-labelledby={"evidence-summary-title"}>
              <h1 id={"evidence-summary-title"}>선택한 근거 요약</h1>
              <div className={"evidence-summary-items"}>
                <span>리포트 <b className={"num"}>2건</b></span><span>공시 (DART) <b className={"num"}>1건</b></span><span>IR 자료 <b className={"num"}>1건</b></span><span>뉴스 <b className={"num"}>1건</b></span><span>핵심지표 <b className={"num"}>5개</b></span>
              </div>
              <strong className={"summary-total"}>총 <span id={"summary-count"}>10</span>개 선택</strong>
            </section>
          </header>

          <div className={"prediction-page-grid"}>
            <section className={"prediction-editor-card"}>
              <form className={"prediction-form"} id={"prediction-form"}>
                <h2>예측 등록하기</h2>

                <fieldset className={"prediction-step direction-step"}>
                  <legend><i>1</i> 예측 방향</legend>
                  <div className={"direction-options"}>
                    <label className={"direction-option up selected"}><input type={"radio"} name={"direction"} defaultValue={"UP"} defaultChecked /><span className={"direction-icon"}>↗</span><span><b>상승 (UP)</b><small>주가가 오를 것으로 예상</small></span></label>
                    <label className={"direction-option down"}><input type={"radio"} name={"direction"} defaultValue={"DOWN"} /><span className={"direction-icon"}>↘</span><span><b>하락 (DOWN)</b><small>주가가 내릴 것으로 예상</small></span></label>
                  </div>
                </fieldset>

                <fieldset className={"prediction-step target-step"}>
                  <legend><i>2</i> 목표가 (KRW)</legend>
                  <label className={"price-input"}><input id={"target-price"} type={"text"} inputMode={"numeric"} defaultValue={"85,000"} aria-label={"목표가"} /><span>원</span></label>
                  <p>현재가 <b className={"num"}>78,500원</b> <strong className={"num"}>(+8.28%)</strong></p>
                </fieldset>

                <fieldset className={"prediction-step period-step"}>
                  <legend><i>3</i> 예측 기간</legend>
                  <div className={"period-options"}>
                    <label className={"period-option selected"}><input type={"radio"} name={"period"} defaultValue={"10"} defaultChecked /><b>10영업일</b><small>~ 2025.06.16</small></label>
                    <label className={"period-option"}><input type={"radio"} name={"period"} defaultValue={"20"} /><b>20영업일</b><small>~ 2025.06.30</small></label>
                    <label className={"period-option direct"}><input type={"radio"} name={"period"} defaultValue={"direct"} /><b>▣ &nbsp; 직접 입력</b><small>마감일 선택</small></label>
                  </div>
                  <label className={"direct-date"} hidden>예측 마감일 <input id={"direct-date"} type={"date"} defaultValue={"2025-06-16"} /></label>
                </fieldset>

                <fieldset className={"prediction-step judgment-step"}>
                  <legend><i>4</i> 내 판단 <small>(선택)</small></legend>
                  <div className={"judgment-editor"}>
                    <div className={"editor-toolbar"} aria-label={"텍스트 서식 도구"}><button type={"button"} data-insert={"# "}>H</button><button type={"button"} data-wrap={"**"}>B</button><button type={"button"} data-wrap={"_"}>I</button><button type={"button"} data-insert={"“ ”"}>❝</button><button type={"button"} data-insert={"• "}>☷</button><button type={"button"} data-insert={"1. "}>☰</button><button type={"button"} data-wrap={"[]()"}>↗</button><button type={"button"} data-wrap={"`"}>&lt;/&gt;</button></div>
                    <textarea id={"judgment-text"} maxLength={500} defaultValue={"AI 서버 수요 증가와 메모리 업황 개선, 파운드리 가동률 회복이 동반되어 실적 개선 흐름이 이어질 것으로 판단합니다.&#10;HBM4 양산 본격화와 모바일 수요 회복도 긍정적입니다."}></textarea>
                    <span className={"editor-count"}><b id={"judgment-count"}>85</b> / 500</span>
                  </div>
                </fieldset>
              </form>

              <section className={"selected-evidence"} aria-labelledby={"selected-evidence-title"}>
                <div className={"selected-evidence-head"}><h2 id={"selected-evidence-title"}>선택한 근거 (<span id={"selected-count"}>10</span>)</h2><button id={"add-ai-evidence"} type={"button"}>AI 근거 추가하기</button></div>

                <div className={"evidence-source-list"} id={"evidence-source-list"}>
                  <article className={"evidence-source selected"} data-evidence-count={"1"}><div className={"evidence-source-title"}><i className={"source-dart"}>DART</i><h3>DART 요약</h3><time>2025.05.31</time><label><input className={"evidence-toggle"} type={"checkbox"} defaultChecked /><span>✓ 선택됨</span></label></div><ul><li>1분기 영업이익 6.7조원 (YoY +12%, QoQ +12.3%)</li><li>반도체 사업 영업이익 1.1조원으로 흑자 전환</li></ul><a href={"#"}>원문 보기</a></article>
                  <article className={"evidence-source selected"} data-evidence-count={"1"}><div className={"evidence-source-title"}><i className={"source-ir"}>IR</i><h3>IR 핵심 내용</h3><time>2025.05.30</time><label><input className={"evidence-toggle"} type={"checkbox"} defaultChecked /><span>✓ 선택됨</span></label></div><ul><li>AI 메모리 시장 성장으로 HBM 수요 증가 지속 전망</li><li>2나노 양산 개선이 본격화, 파운드리 고객 확대</li></ul><a href={"#"}>원문 보기</a></article>
                  <article className={"evidence-source selected"} data-evidence-count={"1"}><div className={"evidence-source-title"}><i className={"source-news"}>뉴스</i><h3>최근 뉴스 요약</h3><time>2025.06.02</time><label><input className={"evidence-toggle"} type={"checkbox"} defaultChecked /><span>✓ 선택됨</span></label></div><ul><li>삼성전자, 엔비디아 HBM4 12단 공급 독점 가능성</li><li>글로벌 스마트폰 시장 회복세 본격화 (ZDNet)</li></ul><a href={"#"}>더 보기</a></article>
                </div>

                <section className={"key-metrics"} aria-labelledby={"key-metrics-title"}>
                  <h3 id={"key-metrics-title"}><i>▣</i> 핵심 지표 (5개)</h3>
                  <div className={"metric-select-grid"}>
                    <label><input className={"metric-toggle"} type={"checkbox"} defaultChecked /><span>PER (TTM)<b>17.21배</b><i>✓</i></span></label>
                    <label><input className={"metric-toggle"} type={"checkbox"} defaultChecked /><span>PBR (TTM)<b>1.21배</b><i>✓</i></span></label>
                    <label><input className={"metric-toggle"} type={"checkbox"} defaultChecked /><span>EPS (TTM)<b>4,554원</b><i>✓</i></span></label>
                    <label><input className={"metric-toggle"} type={"checkbox"} defaultChecked /><span>ROE (TTM)<b>7.23%</b><i>✓</i></span></label>
                    <label><input className={"metric-toggle"} type={"checkbox"} defaultChecked /><span>영업이익률<b>17.8%</b><i>✓</i></span></label>
                  </div>
                </section>

                <article className={"ai-thesis"} id={"ai-thesis"}><h3><i>✣</i> AI 한줄 요약 (Thesis)</h3><p>메모리 업황 회복과 AI 수요 확대, 파운드리 개선이 실적 턴어라운드를 견인하며 단기 상승이 기대됩니다.</p></article>
              </section>
            </section>

            <aside className={"onchain-preview"} aria-labelledby={"onchain-preview-title"}>
              <h2 id={"onchain-preview-title"}>온체인 기록 미리보기 <span title={"실제 등록 전 예상 값입니다"}>ⓘ</span></h2>
              <figure className={"onchain-art"} aria-hidden={"true"}><img src={"/assets/onchain-blocks-transparent-v1.png"} alt={""} /></figure>
              <dl className={"preview-details"}>
                <div><dt>종목</dt><dd>삼성전자 (005930)</dd></div>
                <div><dt>예측 방향</dt><dd id={"preview-direction"} className={"preview-direction up"}>↗ &nbsp;상승 (UP)</dd></div>
                <div><dt>목표가</dt><dd id={"preview-price"} className={"num"}>85,000원</dd></div>
                <div><dt>예측 기간</dt><dd id={"preview-period"}>10영업일 (~ 2025.06.16)</dd></div>
                <div><dt>예측 시각</dt><dd className={"num"}>2025.06.02 16:20:45 (KST)</dd></div>
                <div><dt>사용자 ID</dt><dd className={"num"}>antena_user_12</dd></div>
                <div><dt>근거 해시 (예상)</dt><dd className={"num"}>a9f3b2e7...d4c8f21e &nbsp;⧉</dd></div>
                <div><dt>선택 근거 수</dt><dd><span id={"preview-count"}>10</span>개</dd></div>
              </dl>

              <section className={"prediction-warning"}><h3>❕ 유의사항</h3><ul><li>예측은 투자 참고용 정보이며, 투자 결과에 대한 책임은 투자자 본인에게 있습니다.</li><li>블록체인에 기록되어 수정·삭제가 불가능합니다.</li><li>신중한 판단 후 예측을 등록해 주세요.</li></ul></section>
              <button className={"prediction-submit"} id={"prediction-submit"} type={"button"}>예측 등록하기</button>
              <p className={"chain-safe"} id={"chain-status"}>♢ &nbsp; 블록체인에 안전하게 기록됩니다.</p>
            </aside>
          </div>
        </div>
      </main>

      <div className={"prediction-modal"} id={"prediction-modal"} hidden>
        <button className={"prediction-modal-backdrop"} type={"button"} aria-label={"팝업 닫기"} data-modal-close></button>
        <section className={"prediction-progress-dialog"} role={"dialog"} aria-modal={"true"} aria-labelledby={"progress-modal-title"} tabIndex={-1}>
          <header className={"progress-modal-head"}><h2 id={"progress-modal-title"}>예측을 블록에 기록하고 있어요</h2><button className={"progress-modal-close"} type={"button"} aria-label={"닫기"} data-modal-close>×</button></header>

          <div className={"carrier-stage"} id={"carrier-stage"}>
            <h3>캐릭터들이 예측 블록을 안전하게 옮기고 있어요</h3>
            <div className={"carrier-lane"} aria-hidden={"true"}>
              <span className={"carrier-route"}></span>
              <img className={"block-carrier carrier-one"} src={"/assets/prediction-carrier-right-v1.png"} alt={""} />
              <img className={"block-carrier carrier-two"} src={"/assets/prediction-carrier-right-v1.png"} alt={""} />
              <img className={"block-carrier carrier-three"} src={"/assets/prediction-carrier-right-v1.png"} alt={""} />
              <img className={"block-carrier carrier-four"} src={"/assets/prediction-carrier-right-v1.png"} alt={""} />
              <img className={"destination-vault"} src={"/assets/prediction-vault-v1.png"} alt={""} />
              <i className={"vault-spark spark-one"}></i><i className={"vault-spark spark-two"}></i><i className={"vault-spark spark-three"}></i>
            </div>
          </div>

          <ol className={"chain-progress-list"} aria-label={"예측 등록 진행 단계"}>
            <li className={"done"} data-progress={"signature"}><i>✓</i><span><strong>지갑 서명 완료</strong><small>사용자 확인이 완료되었습니다.</small></span></li>
            <li className={"done"} data-progress={"hash"}><i>✓</i><span><strong>커밋 해시 생성</strong><small>선택한 근거를 하나의 기록으로 묶었습니다.</small></span></li>
            <li className={"active"} data-progress={"anchor"}><i><b></b></i><span><strong>머클 앵커 대기</strong><small>블록에 기록할 차례를 기다리고 있습니다.</small></span></li>
          </ol>

          <p className={"progress-immutability"}>♙ <span>앵커가 완료되면 이 예측은 누구도, 나조차도 수정할 수 없습니다.</span></p>
          <footer className={"progress-modal-actions"}><button type={"button"} className={"modal-secondary"} data-modal-close>닫기</button><button type={"button"} className={"modal-primary"} id={"go-predictions"} disabled>내 예측 내역으로</button></footer>
        </section>
      </div>
    </>
  )
}

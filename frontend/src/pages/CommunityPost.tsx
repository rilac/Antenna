export default function CommunityPost() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner community"}>

          <div className={"cm-main"}>
            <nav className={"cm-crumb"} aria-label={"위치"}>
              <a href={"/community"}>커뮤니티</a><i>›</i><span>리포트 공유</span>
            </nav>

            {/* 게시글 */}
            <article className={"cm-card"}>
              <span className={"cm-cat"}>리포트 공유</span>
              <h1 className={"cm-title"}>반도체훈련소 HBM4 리포트 읽어본 사람?</h1>

              <div className={"cm-byline"}>
                <i className={"cm-avatar s av-1"}>주</i>
                <b>주린이탈출중</b>
                <span className={"cm-lv"}>Lv.14</span>
                <time dateTime={"2026-08-26T10:42"}>2026.08.26 10:42</time>
                <span className={"cm-sep"}>·</span>
                <span className={"cm-views num"}>
                  <svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"}><path d={"M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"} /><circle cx={"12"} cy={"12"} r={"3"} /></svg>
                  3,721
                </span>
                <button className={"cm-kebab"} type={"button"} aria-label={"게시글 메뉴"}>⋮</button>
              </div>

              <p className={"cm-body"}>이번 HBM4 리포트를 읽어봤는데 공급 변화 부분이 특히 흥미로웠어요.<br />SK하이닉스뿐 아니라 장비주까지 같이 봐야 한다는 의견에 다들 동의하시나요?<br />저는 단기 실적보다 내년 양산 일정이 더 중요하다고 생각합니다.</p>

              <div className={"cm-tags"}>
                <span>SK하이닉스</span><span>한미반도체</span><span>HBM</span>
              </div>

              {/* 공유된 리포트 */}
              <section className={"cm-report"} aria-label={"공유된 리포트"}>
                <p className={"cm-report-flag"}>
                  <svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 3 5 5.6v5.9c0 4.2 3 7.4 7 8.5 4-1.1 7-4.3 7-8.5V5.6z"} /><path d={"m9 12 2.2 2.2L15.5 10"} /></svg>
                  공유된 리포트
                </p>

                <div className={"cm-report-grid"}>
                  <div className={"cm-report-author"}>
                    <span className={"cm-verified"}>
                      <i className={"cm-avatar l av-5"}>반</i>
                      <span className={"cm-check"}><svg width={"13"} height={"13"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></span>
                    </span>
                    <b>반도체훈련소</b>
                    <span>신뢰도 82.4</span>
                  </div>

                  <div className={"cm-report-main"}>
                    <h3>HBM4 경쟁 구도와 국내 수혜주</h3>
                    <p>HBM4 양산 일정과 주요 공급망 변화를 바탕으로 핵심 수혜 기업을 분석합니다. SK하이닉스를 중심으로 한 수혜 구도와 장비·소재 업종의 투자 포인트를 제시합니다.</p>
                    <div className={"cm-chips"}><span>반도체</span><span>HBM</span><span>산업분석</span></div>
                    <p className={"cm-report-meta"}>
                      <span className={"num"}>2026.08.26</span>
                      <span className={"cm-sep"}>·</span>
                      <span>12분</span>
                      <span className={"cm-verified-pill"}>
                        <svg width={"13"} height={"13"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2.2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 3 5 5.6v5.9c0 4.2 3 7.4 7 8.5 4-1.1 7-4.3 7-8.5V5.6z"} /><path d={"m9 12 2.2 2.2L15.5 10"} /></svg>
                        예측 근거 검증됨
                      </span>
                    </p>
                  </div>

                  <div className={"cm-report-side"}>
                    <img className={"cm-thumb"} src={"/assets/report-hbm4-feature-v1.png"} alt={"HBM4 경쟁 구도와 국내 수혜주 리포트 대표 이미지"} />
                    <div className={"cm-report-cta"}>
                      <a className={"cm-btn-outline"} href={"/reports"}>
                        리포트 원문 보기
                        <svg width={"15"} height={"15"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M14 4h6v6"} /><path d={"M20 4 11 13"} /><path d={"M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"} /></svg>
                      </a>
                      <small>Antena 리서치에서 공유됨</small>
                    </div>
                  </div>
                </div>
              </section>

              {/* 액션 */}
              <div className={"cm-actions"}>
                <button className={"cm-act"} id={"cm-like"} type={"button"} aria-pressed={"false"}>
                  <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 20s-7.5-4.6-9.3-9A5 5 0 0 1 12 6.5 5 5 0 0 1 21.3 11c-1.8 4.4-9.3 9-9.3 9z"} /></svg>
                  공감 <span className={"num"} id={"cm-like-count"}>201</span>
                </button>
                <a className={"cm-act"} href={"#cm-comments"}>
                  <svg width={"19"} height={"19"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>
                  댓글 <span className={"num"}>67</span>
                </a>
                <div className={"cm-right"}>
                  <button className={"cm-act"} id={"cm-bookmark"} type={"button"} aria-pressed={"false"}>
                    <svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M6 3h12v18l-6-4.5L6 21z"} /></svg>
                    북마크
                  </button>
                  <button className={"cm-act"} type={"button"}>
                    <svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 16V4"} /><path d={"m7 9 5-5 5 5"} /><path d={"M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4"} /></svg>
                    공유
                  </button>
                </div>
              </div>
            </article>

            {/* 댓글 */}
            <section className={"cm-card"} id={"cm-comments"} aria-labelledby={"cm-comments-title"}>
              <div className={"cm-comment-head"}>
                <h2 id={"cm-comments-title"}>댓글 <span className={"num"}>67</span></h2>
                <div className={"cm-sort"} role={"group"} aria-label={"댓글 정렬"}>
                  <button className={"on"} type={"button"} aria-pressed={"true"}>인기순</button>
                  <button type={"button"} aria-pressed={"false"}>최신순</button>
                </div>
                <div className={"cm-pager"}>
                  <button type={"button"}>‹ 이전 글</button>
                  <button type={"button"}>다음 글 ›</button>
                </div>
              </div>

              <form className={"cm-form"}>
                <i className={"cm-avatar m av-4"}>나</i>
                <span className={"cm-input"}>
                  <label className={"sr-only"} htmlFor={"cm-new"}>댓글 입력</label>
                  <input id={"cm-new"} type={"text"} placeholder={"의견을 남겨보세요."} />
                  <button className={"cm-at"} type={"button"} aria-label={"사용자 언급"}>@</button>
                </span>
                <button className={"cm-submit"} type={"submit"}>댓글 등록</button>
              </form>

              <ul className={"cm-list"}>
                <li className={"cm-item"}>
                  <i className={"cm-avatar s av-2"}>데</i>
                  <div className={"cm-item-main"}>
                    <p className={"cm-item-name"}><b>데이터탐험</b> <span className={"cm-lv"}>Lv.16</span></p>
                    <p className={"cm-item-text"}>장비주를 역시 시장보다 반주 타이밍을 먼저 봐야 할 것 같아요.</p>
                    <p className={"cm-item-meta"}>
                      <time className={"num"} dateTime={"2026-08-26T10:58"}>2026.08.26 10:58</time>
                      <span>공감 <b className={"num"}>98</b></span>
                      <button type={"button"}>답글</button>
                      <button type={"button"}>신고</button>
                    </p>
                  </div>
                  <div className={"cm-item-side"}>
                    <button type={"button"} aria-label={"공감"}>♡</button>
                    <button type={"button"} aria-label={"댓글 메뉴"}>⋮</button>
                  </div>
                </li>

                <li className={"cm-item cm-reply"}>
                  <i className={"cm-avatar s av-1"}>주</i>
                  <div className={"cm-item-main"}>
                    <p className={"cm-item-name"}><b>주린이탈출중</b> <span className={"cm-writer"}>작성자</span> <span className={"cm-lv"}>Lv.14</span></p>
                    <p className={"cm-item-text"}>맞아요. 그래서 리포트의 3페이지 수주 데이터가 중요해 보였습니다.</p>
                    <p className={"cm-item-meta"}>
                      <time className={"num"} dateTime={"2026-08-26T11:05"}>2026.08.26 11:05</time>
                      <span>공감 <b className={"num"}>54</b></span>
                      <button type={"button"}>답글</button>
                      <button type={"button"}>신고</button>
                    </p>
                  </div>
                  <div className={"cm-item-side"}>
                    <button type={"button"} aria-label={"공감"}>♡</button>
                    <button type={"button"} aria-label={"댓글 메뉴"}>⋮</button>
                  </div>
                </li>

                <li className={"cm-item"}>
                  <i className={"cm-avatar s av-3"}>가</i>
                  <div className={"cm-item-main"}>
                    <p className={"cm-item-name"}><b>가치투자러</b> <span className={"cm-lv"}>Lv.20</span></p>
                    <p className={"cm-item-text"}>밸류에이션 부담은 어떻게 생각하시나요?</p>
                    <p className={"cm-item-meta"}>
                      <time className={"num"} dateTime={"2026-08-26T11:20"}>2026.08.26 11:20</time>
                      <span>공감 <b className={"num"}>61</b></span>
                      <button type={"button"}>답글</button>
                      <button type={"button"}>신고</button>
                    </p>
                  </div>
                  <div className={"cm-item-side"}>
                    <button type={"button"} aria-label={"공감"}>♡</button>
                    <button type={"button"} aria-label={"댓글 메뉴"}>⋮</button>
                  </div>
                </li>

                <li className={"cm-item cm-reply"}>
                  <i className={"cm-avatar s av-5"}>안</i>
                  <div className={"cm-item-main"}>
                    <p className={"cm-item-name"}><b>안테나프로</b> <span className={"cm-lv"}>Lv.24</span></p>
                    <p className={"cm-item-text"}>PER은 높지만 이익 성장을 고려하면 피어 대비 합리적이라고 봅니다.</p>
                    <p className={"cm-item-meta"}>
                      <time className={"num"} dateTime={"2026-08-26T11:28"}>2026.08.26 11:28</time>
                      <span>공감 <b className={"num"}>53</b></span>
                      <button type={"button"}>답글</button>
                      <button type={"button"}>신고</button>
                    </p>
                  </div>
                  <div className={"cm-item-side"}>
                    <button type={"button"} aria-label={"공감"}>♡</button>
                    <button type={"button"} aria-label={"댓글 메뉴"}>⋮</button>
                  </div>
                </li>
              </ul>
            </section>

            <p className={"cm-notice"}>
              <svg width={"17"} height={"17"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 3 5 5.6v5.9c0 4.2 3 7.4 7 8.5 4-1.1 7-4.3 7-8.5V5.6z"} /></svg>
              서로를 존중하는 건강한 토론 문화를 만들어주세요. 욕설, 비방, 허위 정보는 신고 후 검토됩니다.
              <a href={"#"}>커뮤니티 운영 정책 보기 ›</a>
            </p>
          </div>

          {/* 오른쪽 */}
          <aside className={"cm-side"}>
            <section className={"cm-side-card"}>
              <div className={"cm-side-head"}><h2>이 글에서 언급된 종목</h2></div>

              <a className={"cm-quote"} href={"/stock-detail?code=000660&name=SK%ED%95%98%EC%9D%B4%EB%8B%89%EC%8A%A4&market=KOSPI&sector=%EB%B0%98%EB%8F%84%EC%B2%B4&price=231500&change=2.15&per=9.8&pbr=1.72&logo=logo-skhy&mark=S"}>
                <span>
                  <span className={"cm-quote-name"}><b>SK하이닉스</b> <span>000660</span></span>
                  <span className={"cm-quote-price"}><strong>231,500</strong> <em className={"rise"}>+2.15%</em></span>
                </span>
                <svg viewBox={"0 0 108 42"} fill={"none"} aria-hidden={"true"}>
                  <path d={"M2 34 14 30 24 33 34 24 44 27 54 18 64 21 74 12 84 15 94 8 106 4V42H2z"} fill={"#ff323d"} opacity={".08"} />
                  <path d={"M2 34 14 30 24 33 34 24 44 27 54 18 64 21 74 12 84 15 94 8 106 4"} stroke={"#ff323d"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"} />
                </svg>
              </a>

              <a className={"cm-quote"} href={"/stock-detail?code=042700&name=%ED%95%9C%EB%AF%B8%EB%B0%98%EB%8F%84%EC%B2%B4&market=KOSPI&sector=%EB%B0%98%EB%8F%84%EC%B2%B4&price=78300&change=-1.32&per=21.4&pbr=4.12&mark=%ED%95%9C"}>
                <span>
                  <span className={"cm-quote-name"}><b>한미반도체</b> <span>042700</span></span>
                  <span className={"cm-quote-price"}><strong>78,300</strong> <em className={"fall"}>-1.32%</em></span>
                </span>
                <svg viewBox={"0 0 108 42"} fill={"none"} aria-hidden={"true"}>
                  <path d={"M2 8 14 13 24 10 34 19 44 15 54 24 64 20 74 29 84 25 94 33 106 30V42H2z"} fill={"#2464ee"} opacity={".08"} />
                  <path d={"M2 8 14 13 24 10 34 19 44 15 54 24 64 20 74 29 84 25 94 33 106 30"} stroke={"#2464ee"} strokeWidth={"2"} strokeLinecap={"round"} strokeLinejoin={"round"} />
                </svg>
              </a>

              <a className={"cm-side-more"} href={"/stocks"}>더 많은 종목 보기 ›</a>
            </section>

            <section className={"cm-side-card"}>
              <div className={"cm-author-card"}>
                <span className={"cm-verified"}>
                  <i className={"cm-avatar l av-5"}>반</i>
                  <span className={"cm-check"}><svg width={"13"} height={"13"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></span>
                </span>
                <div>
                  <h3>반도체훈련소 <span className={"cm-check-sm"}><svg width={"11"} height={"11"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></span></h3>
                  <p className={"cm-author-stats"}>
                    <span>신뢰도 <b className={"num"}>82.4</b></span>
                    <span>적중률 <b className={"num"}>78.6%</b></span>
                    <span>구독자 <b className={"num"}>12.3K</b></span>
                  </p>
                </div>
              </div>
              <div className={"cm-author-btns"}>
                <a className={"cm-ghost"} href={"/market"}>프로필 보기</a>
                <a className={"cm-solid"} href={"#"}>구독</a>
              </div>
            </section>

            <section className={"cm-side-card"}>
              <div className={"cm-side-head"}><h2>관련 인기글</h2><a href={"/community"}>더보기 ›</a></div>
              <ul className={"cm-hot"}>
                <li><a href={"/community/post"}>HBM4 양산 일정 총정리</a><span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>48</span></li>
                <li><a href={"/community/post"}>SK하이닉스 2H 실적 전망</a><span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>36</span></li>
                <li><a href={"/community/post"}>장비주 수주 사이클 체크포인트</a><span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>29</span></li>
                <li><a href={"/community/post"}>HBM4 경쟁사 동향 업데이트</a><span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>22</span></li>
              </ul>
            </section>

            <section className={"cm-side-card cm-guide"}>
              <h2>
                <svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 3 5 5.6v5.9c0 4.2 3 7.4 7 8.5 4-1.1 7-4.3 7-8.5V5.6z"} /></svg>
                건전한 투자 문화, 함께 만들어가요
              </h2>
              <p>허위 정보, 비방, 불건전한 게시글은 신고해주세요.</p>
              <a className={"cm-side-more"} href={"#"}>게시글 신고하기 ›</a>
            </section>
          </aside>

        </div>
      </main>
    </>
  )
}

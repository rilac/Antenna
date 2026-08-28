export default function Community() {
  return (
    <>
      <main className={"main"}>
        <div className={"main-inner community"}>

          <div className={"cm-main"}>
            <header className={"cl-head"}>
              <div>
                <h1>커뮤니티</h1>
                <p>같은 종목을 보는 사람들과 근거를 나눠보세요.</p>
              </div>
              <a className={"cl-write"} href={"/community/post"}>
                <svg width={"17"} height={"17"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M4 20h4L20 8l-4-4L4 16z"} /><path d={"m14 6 4 4"} /></svg>
                글쓰기
              </a>
            </header>

            <div className={"cl-scopes"} role={"group"} aria-label={"범위"}>
              <button className={"on"} type={"button"} data-scope={"all"}>전체 글</button>
              <button type={"button"} data-scope={"mine"}>내가 쓴 글 <b>2</b></button>
            </div>

            <div className={"cl-cats"} role={"group"} aria-label={"분류"}>
              <button className={"on"} type={"button"} data-cat={"all"}>전체</button>
              <button type={"button"} data-cat={"리포트 공유"}>리포트 공유</button>
              <button type={"button"} data-cat={"종목 토론"}>종목 토론</button>
              <button type={"button"} data-cat={"시황·매크로"}>시황·매크로</button>
              <button type={"button"} data-cat={"질문"}>질문</button>
              <button type={"button"} data-cat={"인증·후기"}>인증·후기</button>
            </div>

            <div className={"cl-bar"}>
              <p className={"cl-total"}>전체 <b id={"cl-count"}>6</b>개</p>
              <div className={"cl-sorts"} role={"group"} aria-label={"정렬"}>
                <button className={"on"} type={"button"} data-sort={"date"}>최신순</button>
                <button type={"button"} data-sort={"likes"}>공감순</button>
                <button type={"button"} data-sort={"comments"}>댓글순</button>
              </div>
            </div>

            <ul className={"cl-list"} id={"cl-list"}>

              <li className={"cl-post"} data-cat={"종목 토론"} data-mine={"true"} data-date={"20260827"} data-likes={"46"} data-comments={"12"}>
                <div className={"cl-post-main"}>
                  <p className={"cl-badges"}>
                    <span className={"cm-cat"} style={{ margin: '0' }}>종목 토론</span>
                    <span className={"cl-mine"}>내가 쓴 글</span>
                  </p>
                  <h2><a href={"/community/post"}>HBM4 수혜주, 장비주까지 봐야 하는 이유</a></h2>
                  <p className={"cl-excerpt"}>공급망을 단계별로 나눠서 보면 후공정 장비 쪽 수주가 먼저 반응하는 패턴이 반복됩니다. 근거로 쓴 공시 목록도 함께 정리했습니다.</p>
                  <div className={"cl-post-foot"}>
                    <span className={"cl-poster"}><i className={"cm-avatar s av-4"}>안</i><b>안테나님</b><span className={"cm-lv"}>Lv.12</span></span>
                    <span className={"cl-dot"}>·</span>
                    <time dateTime={"2026-08-27T09:10"}>2026.08.27 09:10</time>
                    <span className={"cl-counts"}>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M12 20s-7.5-4.6-9.3-9A5 5 0 0 1 12 6.5 5 5 0 0 1 21.3 11c-1.8 4.4-9.3 9-9.3 9z"} /></svg>46</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>12</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"}><path d={"M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"} /><circle cx={"12"} cy={"12"} r={"3"} /></svg>1,204</span>
                    </span>
                  </div>
                </div>
              </li>

              <li className={"cl-post"} data-cat={"인증·후기"} data-mine={"true"} data-date={"20260822"} data-likes={"28"} data-comments={"7"}>
                <div className={"cl-post-main"}>
                  <p className={"cl-badges"}>
                    <span className={"cm-cat"} style={{ margin: '0' }}>인증·후기</span>
                    <span className={"cl-mine"}>내가 쓴 글</span>
                    <span className={"cl-draft"}>임시 저장</span>
                  </p>
                  <h2><a href={"/community/post"}>예측 20건 회고 — 맞춘 것보다 틀린 게 남는다</a></h2>
                  <p className={"cl-excerpt"}>방향은 맞혔지만 목표가에서 크게 빗나간 사례를 모아봤습니다. 확신도를 높게 준 예측이 오히려 오차가 컸습니다.</p>
                  <div className={"cl-post-foot"}>
                    <span className={"cl-poster"}><i className={"cm-avatar s av-4"}>안</i><b>안테나님</b><span className={"cm-lv"}>Lv.12</span></span>
                    <span className={"cl-dot"}>·</span>
                    <time dateTime={"2026-08-22T21:40"}>2026.08.22 21:40</time>
                    <span className={"cl-counts"}>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M12 20s-7.5-4.6-9.3-9A5 5 0 0 1 12 6.5 5 5 0 0 1 21.3 11c-1.8 4.4-9.3 9-9.3 9z"} /></svg>28</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>7</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"}><path d={"M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"} /><circle cx={"12"} cy={"12"} r={"3"} /></svg>640</span>
                    </span>
                  </div>
                </div>
              </li>


              <li className={"cl-post"} data-cat={"리포트 공유"} data-date={"20260826"} data-likes={"201"} data-comments={"67"}>
                <div className={"cl-post-main"}>
                  <p className={"cl-badges"}>
                    <span className={"cm-cat"} style={{ margin: '0' }}>리포트 공유</span>
                    <span className={"cl-attach"}>
                      <svg width={"12"} height={"12"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2.2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 3 5 5.6v5.9c0 4.2 3 7.4 7 8.5 4-1.1 7-4.3 7-8.5V5.6z"} /><path d={"m9 12 2.2 2.2L15.5 10"} /></svg>
                      검증된 리포트 첨부
                    </span>
                  </p>
                  <h2><a href={"/community/post"}>반도체훈련소 HBM4 리포트 읽어본 사람?</a></h2>
                  <p className={"cl-excerpt"}>이번 HBM4 리포트를 읽어봤는데 공급 변화 부분이 특히 흥미로웠어요. SK하이닉스뿐 아니라 장비주까지 같이 봐야 한다는 의견에 다들 동의하시나요?</p>
                  <div className={"cl-post-foot"}>
                    <span className={"cl-poster"}><i className={"cm-avatar s av-1"}>주</i><b>주린이탈출중</b><span className={"cm-lv"}>Lv.14</span></span>
                    <span className={"cl-dot"}>·</span>
                    <time dateTime={"2026-08-26T10:42"}>2026.08.26 10:42</time>
                    <span className={"cl-counts"}>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M12 20s-7.5-4.6-9.3-9A5 5 0 0 1 12 6.5 5 5 0 0 1 21.3 11c-1.8 4.4-9.3 9-9.3 9z"} /></svg>201</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>67</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"}><path d={"M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"} /><circle cx={"12"} cy={"12"} r={"3"} /></svg>3,721</span>
                    </span>
                  </div>
                </div>
                <img className={"cl-thumb"} src={"/assets/report-hbm4-feature-v1.png"} alt={"공유된 HBM4 리포트 대표 이미지"} />
              </li>

              <li className={"cl-post"} data-cat={"종목 토론"} data-date={"20260826"} data-likes={"132"} data-comments={"48"}>
                <div className={"cl-post-main"}>
                  <p className={"cl-badges"}><span className={"cm-cat"} style={{ margin: '0' }}>종목 토론</span></p>
                  <h2><a href={"/community/post"}>HBM4 양산 일정 총정리</a></h2>
                  <p className={"cl-excerpt"}>공개된 로드맵과 컨퍼런스콜 발언을 모아 분기별 양산 일정을 정리했습니다. 빠진 내용 있으면 댓글로 알려주세요.</p>
                  <div className={"cl-post-foot"}>
                    <span className={"cl-poster"}><i className={"cm-avatar s av-2"}>데</i><b>데이터탐험</b><span className={"cm-lv"}>Lv.16</span></span>
                    <span className={"cl-dot"}>·</span>
                    <time dateTime={"2026-08-26T09:14"}>2026.08.26 09:14</time>
                    <span className={"cl-counts"}>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M12 20s-7.5-4.6-9.3-9A5 5 0 0 1 12 6.5 5 5 0 0 1 21.3 11c-1.8 4.4-9.3 9-9.3 9z"} /></svg>132</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>48</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"}><path d={"M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"} /><circle cx={"12"} cy={"12"} r={"3"} /></svg>2,140</span>
                    </span>
                  </div>
                </div>
              </li>

              <li className={"cl-post"} data-cat={"종목 토론"} data-date={"20260825"} data-likes={"118"} data-comments={"36"}>
                <div className={"cl-post-main"}>
                  <p className={"cl-badges"}>
                    <span className={"cm-cat"} style={{ margin: '0' }}>종목 토론</span>
                    <span className={"cl-attach"}>
                      <svg width={"12"} height={"12"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"2.2"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 3 5 5.6v5.9c0 4.2 3 7.4 7 8.5 4-1.1 7-4.3 7-8.5V5.6z"} /><path d={"m9 12 2.2 2.2L15.5 10"} /></svg>
                      검증된 리포트 첨부
                    </span>
                  </p>
                  <h2><a href={"/community/post"}>SK하이닉스 2H 실적, 시나리오별로 뜯어봤습니다</a></h2>
                  <p className={"cl-excerpt"}>하반기 출하량과 판가 가정을 나눠 영업이익을 추정해봤습니다. 보수적 가정에서도 컨센서스는 넘습니다.</p>
                  <div className={"cl-post-foot"}>
                    <span className={"cl-poster"}><i className={"cm-avatar s av-5"}>반</i><b>반도체훈련소</b><span className={"cm-check-sm"}><svg width={"11"} height={"11"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"4"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"m5 13 4.5 4.5L19 7"} /></svg></span></span>
                    <span className={"cl-dot"}>·</span>
                    <time dateTime={"2026-08-25T16:30"}>2026.08.25 16:30</time>
                    <span className={"cl-counts"}>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M12 20s-7.5-4.6-9.3-9A5 5 0 0 1 12 6.5 5 5 0 0 1 21.3 11c-1.8 4.4-9.3 9-9.3 9z"} /></svg>118</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>36</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"}><path d={"M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"} /><circle cx={"12"} cy={"12"} r={"3"} /></svg>1,880</span>
                    </span>
                  </div>
                </div>
              </li>

              <li className={"cl-post"} data-cat={"시황·매크로"} data-date={"20260824"} data-likes={"94"} data-comments={"29"}>
                <div className={"cl-post-main"}>
                  <p className={"cl-badges"}><span className={"cm-cat"} style={{ margin: '0' }}>시황·매크로</span></p>
                  <h2><a href={"/community/post"}>외국인 수급, 이번 주가 분기점입니다</a></h2>
                  <p className={"cl-excerpt"}>업종별 순매수 전환 신호와 선물 포지션 변화를 같이 보고 있습니다. 반도체 쪽은 이미 방향을 튼 것 같네요.</p>
                  <div className={"cl-post-foot"}>
                    <span className={"cl-poster"}><i className={"cm-avatar s av-3"}>가</i><b>가치투자러</b><span className={"cm-lv"}>Lv.20</span></span>
                    <span className={"cl-dot"}>·</span>
                    <time dateTime={"2026-08-24T11:05"}>2026.08.24 11:05</time>
                    <span className={"cl-counts"}>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M12 20s-7.5-4.6-9.3-9A5 5 0 0 1 12 6.5 5 5 0 0 1 21.3 11c-1.8 4.4-9.3 9-9.3 9z"} /></svg>94</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>29</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"}><path d={"M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"} /><circle cx={"12"} cy={"12"} r={"3"} /></svg>1,410</span>
                    </span>
                  </div>
                </div>
              </li>

              <li className={"cl-post"} data-cat={"질문"} data-date={"20260823"} data-likes={"61"} data-comments={"18"}>
                <div className={"cl-post-main"}>
                  <p className={"cl-badges"}><span className={"cm-cat"} style={{ margin: '0' }}>질문</span></p>
                  <h2><a href={"/community/post"}>PER 높은 성장주, 어떤 기준으로 보시나요?</a></h2>
                  <p className={"cl-excerpt"}>이익 성장률을 감안한 피어 비교를 하고 싶은데 다들 어떤 지표를 쓰시는지 궁금합니다.</p>
                  <div className={"cl-post-foot"}>
                    <span className={"cl-poster"}><i className={"cm-avatar s av-5"}>안</i><b>안테나프로</b><span className={"cm-lv"}>Lv.24</span></span>
                    <span className={"cl-dot"}>·</span>
                    <time dateTime={"2026-08-23T20:41"}>2026.08.23 20:41</time>
                    <span className={"cl-counts"}>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M12 20s-7.5-4.6-9.3-9A5 5 0 0 1 12 6.5 5 5 0 0 1 21.3 11c-1.8 4.4-9.3 9-9.3 9z"} /></svg>61</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>18</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"}><path d={"M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"} /><circle cx={"12"} cy={"12"} r={"3"} /></svg>860</span>
                    </span>
                  </div>
                </div>
              </li>

              <li className={"cl-post"} data-cat={"인증·후기"} data-date={"20260823"} data-likes={"77"} data-comments={"14"}>
                <div className={"cl-post-main"}>
                  <p className={"cl-badges"}><span className={"cm-cat"} style={{ margin: '0' }}>인증·후기</span></p>
                  <h2><a href={"/community/post"}>모의 투자 시즌 7 결과 공유합니다</a></h2>
                  <p className={"cl-excerpt"}>2008 금융위기 구간을 돌려본 매매 기록과, 실패했다고 생각하는 판단 세 가지를 정리했습니다.</p>
                  <div className={"cl-post-foot"}>
                    <span className={"cl-poster"}><i className={"cm-avatar s av-2"}>데</i><b>데이터헌터</b><span className={"cm-lv"}>Lv.16</span></span>
                    <span className={"cl-dot"}>·</span>
                    <time dateTime={"2026-08-23T08:22"}>2026.08.23 08:22</time>
                    <span className={"cl-counts"}>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M12 20s-7.5-4.6-9.3-9A5 5 0 0 1 12 6.5 5 5 0 0 1 21.3 11c-1.8 4.4-9.3 9-9.3 9z"} /></svg>77</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>14</span>
                      <span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"}><path d={"M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"} /><circle cx={"12"} cy={"12"} r={"3"} /></svg>1,020</span>
                    </span>
                  </div>
                </div>
              </li>

            </ul>

            <p className={"cl-empty"} id={"cl-empty"} hidden>조건에 맞는 게시글이 없습니다.</p>
            <button className={"cl-more"} id={"cl-more"} type={"button"}>더 보기 ⌄</button>
          </div>

          {/* 오른쪽 */}
          <aside className={"cm-side"}>
            <section className={"cm-side-card"}>
              <div className={"cm-side-head"}><h2>인기 태그</h2></div>
              <div className={"cl-tagcloud"}>
                <a href={"#"}>HBM</a><a href={"#"}>SK하이닉스</a><a href={"#"}>한미반도체</a>
                <a href={"#"}>2차전지</a><a href={"#"}>외국인 수급</a><a href={"#"}>코스피</a>
                <a href={"#"}>밸류에이션</a><a href={"#"}>모의 투자</a>
              </div>
            </section>

            <section className={"cm-side-card"}>
              <div className={"cm-side-head"}><h2>지금 뜨는 글</h2><a href={"#"}>더보기 ›</a></div>
              <ul className={"cm-hot"}>
                <li><a href={"/community/post"}>반도체훈련소 HBM4 리포트 읽어본 사람?</a><span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>67</span></li>
                <li><a href={"/community/post"}>HBM4 양산 일정 총정리</a><span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>48</span></li>
                <li><a href={"/community/post"}>SK하이닉스 2H 실적 전망</a><span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>36</span></li>
                <li><a href={"/community/post"}>장비주 수주 사이클 체크포인트</a><span><svg width={"14"} height={"14"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.9"} strokeLinejoin={"round"}><path d={"M21 12a8 8 0 0 1-8 8H4l2-3.2A8 8 0 1 1 21 12z"} /></svg>29</span></li>
              </ul>
            </section>

            <section className={"cm-side-card cm-guide"}>
              <h2>
                <svg width={"18"} height={"18"} viewBox={"0 0 24 24"} fill={"none"} stroke={"currentColor"} strokeWidth={"1.8"} strokeLinecap={"round"} strokeLinejoin={"round"}><path d={"M12 3 5 5.6v5.9c0 4.2 3 7.4 7 8.5 4-1.1 7-4.3 7-8.5V5.6z"} /></svg>
                건전한 투자 문화, 함께 만들어가요
              </h2>
              <p>허위 정보, 비방, 불건전한 게시글은 신고해주세요.</p>
              <a className={"cm-side-more"} href={"#"}>커뮤니티 운영 정책 보기 ›</a>
            </section>
          </aside>

        </div>
      </main>
    </>
  )
}

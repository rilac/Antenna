-- ANT-DB-02: append-only 강제.
-- Hibernate(ddl-auto)가 만들지 못하는 트리거만 여기 둔다. 매 부팅마다 실행되므로 전부 멱등(CREATE OR REPLACE).
-- 권한 REVOKE 대신 트리거를 쓰는 이유: ddl-auto 가 돌려면 앱 계정이 테이블 소유자여야 하고,
-- 소유자에게서 권한을 회수할 수 없다. 트리거는 소유자에게도 걸린다.
-- 파일 전체가 한 문장으로 전송된다(spring.sql.init.separator). plpgsql 본문의 ';' 때문이다.

CREATE OR REPLACE FUNCTION antenna_reject_write() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % not allowed', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END
$$;

-- 원장·체결 내역: 어떤 수정·삭제도 없다. 잔액은 SUM(delta), 손익은 체결 재계산으로 구한다.
CREATE OR REPLACE TRIGGER trg_token_ledger_append_only
    BEFORE UPDATE OR DELETE ON token_ledger FOR EACH ROW EXECUTE FUNCTION antenna_reject_write();
CREATE OR REPLACE TRIGGER trg_season_trades_append_only
    BEFORE UPDATE OR DELETE ON season_trades FOR EACH ROW EXECUTE FUNCTION antenna_reject_write();

-- 온체인 이벤트 원본: 삭제 불가. 후속 처리 완료 표시(processed_at)만 바뀔 수 있다.
CREATE OR REPLACE TRIGGER trg_chain_events_no_delete
    BEFORE DELETE ON chain_events FOR EACH ROW EXECUTE FUNCTION antenna_reject_write();

CREATE OR REPLACE FUNCTION antenna_chain_events_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id, NEW.tx_hash, NEW.log_index, NEW.contract_address, NEW.event_name, NEW.block_number, NEW.payload::text)
       IS DISTINCT FROM
       ROW(OLD.id, OLD.tx_hash, OLD.log_index, OLD.contract_address, OLD.event_name, OLD.block_number, OLD.payload::text) THEN
        RAISE EXCEPTION 'chain_events is append-only: only processed_at may change'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE TRIGGER trg_chain_events_guard
    BEFORE UPDATE ON chain_events FOR EACH ROW EXECUTE FUNCTION antenna_chain_events_guard();

-- 예측: 등록 후 수정·삭제 불가. 배치가 채우는 판정 컬럼(status·base_*·settle_*·error_rate·updated_at)만 바뀐다.
-- 커밋 payload 를 이루는 내용(대상·방향·목표가·기간·등록 시각)은 한 글자도 바뀌면 커밋 해시와 어긋난다.
CREATE OR REPLACE TRIGGER trg_predictions_no_delete
    BEFORE DELETE ON predictions FOR EACH ROW EXECUTE FUNCTION antenna_reject_write();

CREATE OR REPLACE FUNCTION antenna_predictions_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id, NEW.user_id, NEW.track, NEW.stock_code, NEW.season_ticker_id,
           NEW.direction, NEW.target_price, NEW.ref_close, NEW.horizon, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.id, OLD.user_id, OLD.track, OLD.stock_code, OLD.season_ticker_id,
           OLD.direction, OLD.target_price, OLD.ref_close, OLD.horizon, OLD.created_at) THEN
        RAISE EXCEPTION 'predictions is append-only: only settlement columns may change'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE TRIGGER trg_predictions_guard
    BEFORE UPDATE ON predictions FOR EACH ROW EXECUTE FUNCTION antenna_predictions_guard();

package ssafy.a507.backend.domain.market.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.account.repository.WatchlistItemRepository;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.market.repository.StockRepository;

/**
 * 같은 종목을 두 탭에서 동시에 담는 경합. exists 검사는 둘 다 통과하고 INSERT 에서 한쪽이
 * UQ(user_id, stock_code)에 걸린다 — 그 결과가 500 이 아니라 409 여야 한다. MockMvc 로는
 * 경합을 만들 수 없어 저장소가 유니크 위반을 던지는 장면만 흉내 낸다.
 */
@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock WatchlistItemRepository watchlistItemRepository;
    @Mock StockRepository stockRepository;
    @Mock DailyQuoteRepository dailyQuoteRepository;
    @Mock UserRepository userRepository;

    @InjectMocks WatchlistService service;

    @Test
    @DisplayName("exists 검사를 지나친 뒤 INSERT 가 유니크에 걸려도 409 DUPLICATE_WATCHLIST_ITEM 이다")
    void 동시_담기의_유니크_위반은_409() {
        given(stockRepository.findById("005930")).willReturn(Optional.of(mock(Stock.class)));
        given(watchlistItemRepository.existsByUser_IdAndStock_Code(1L, "005930")).willReturn(false);
        given(userRepository.getReferenceById(1L)).willReturn(mock(User.class));
        given(watchlistItemRepository.save(any()))
                .willThrow(new DataIntegrityViolationException("uq_watchlist_items_user_stock"));

        assertThatThrownBy(() -> service.add(1L, "005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_WATCHLIST_ITEM);
    }
}

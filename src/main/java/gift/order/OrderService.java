package gift.order;

import gift.member.Member;
import gift.member.MemberRepository;
import gift.option.Option;
import gift.option.OptionRepository;
import gift.wish.WishRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orderRepository;
    private final OptionRepository optionRepository;
    private final WishRepository wishRepository;
    private final MemberRepository memberRepository;
    private final KakaoMessageClient kakaoMessageClient;

    public OrderService(
        OrderRepository orderRepository,
        OptionRepository optionRepository,
        WishRepository wishRepository,
        MemberRepository memberRepository,
        KakaoMessageClient kakaoMessageClient
    ) {
        this.orderRepository = orderRepository;
        this.optionRepository = optionRepository;
        this.wishRepository = wishRepository;
        this.memberRepository = memberRepository;
        this.kakaoMessageClient = kakaoMessageClient;
    }

    public Page<Order> findByMemberId(Long memberId, Pageable pageable) {
        return orderRepository.findByMemberId(memberId, pageable);
    }

    // 주문 흐름:
    // 1. 인증 확인
    // 2. 옵션 검증
    // 3. 재고 차감
    // 4. 포인트 차감
    // 5. 주문 저장
    // 6. 위시리스트 정리
    // 7. 카카오 알림 전송
    @Transactional
    public Order createOrder(Member member, OrderRequest request) {
        // 옵션 검증
        var option = optionRepository.findById(request.optionId())
            .orElseThrow(() -> new NoSuchElementException("옵션이 존재하지 않습니다. optionId=" + request.optionId()));

        // 재고 차감
        option.subtractQuantity(request.quantity());
        optionRepository.save(option);

        // 포인트 차감
        var price = option.getProduct().getPrice() * request.quantity();
        member.deductPoint(price);
        memberRepository.save(member);

        // 주문 저장
        var saved = orderRepository.save(new Order(option, member.getId(), request.quantity(), request.message()));

        // 카카오 알림 전송 (best-effort)
        sendKakaoMessageIfPossible(member, saved, option);
        return saved;
    }

    private void sendKakaoMessageIfPossible(Member member, Order order, Option option) {
        if (member.getKakaoAccessToken() == null) {
            return;
        }
        try {
            var product = option.getProduct();
            kakaoMessageClient.sendToMe(member.getKakaoAccessToken(), order, product);
        } catch (Exception ignored) {
        }
    }
}

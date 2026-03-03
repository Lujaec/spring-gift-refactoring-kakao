package gift.order;

import gift.auth.AuthenticationResolver;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final AuthenticationResolver authenticationResolver;

    public OrderController(
        OrderService orderService,
        AuthenticationResolver authenticationResolver
    ) {
        this.orderService = orderService;
        this.authenticationResolver = authenticationResolver;
    }

    @GetMapping
    public ResponseEntity<?> getOrders(
        @RequestHeader("Authorization") String authorization,
        Pageable pageable
    ) {
        // 인증 확인
        var member = authenticationResolver.extractMember(authorization);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var orders = orderService.findByMemberId(member.getId(), pageable).map(OrderResponse::from);
        return ResponseEntity.ok(orders);
    }

    // 주문 흐름:
    // 1. 인증 확인
    // 2. 옵션 검증
    // 3. 재고 차감
    // 4. 포인트 차감
    // 5. 주문 저장
    // 6. 위시리스트 정리
    // 7. 카카오 알림 전송
    @PostMapping
    public ResponseEntity<?> createOrder(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody OrderRequest request
    ) {
        // 인증 확인
        var member = authenticationResolver.extractMember(authorization);
        if (member == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            var saved = orderService.createOrder(member, request);
            return ResponseEntity.created(URI.create("/api/orders/" + saved.getId()))
                .body(OrderResponse.from(saved));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

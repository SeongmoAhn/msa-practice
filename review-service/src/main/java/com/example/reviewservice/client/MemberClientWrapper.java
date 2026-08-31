package com.example.reviewservice.client;

import com.example.reviewservice.dto.MemberDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberClientWrapper {
    private final MemberServiceClient memberClient;

    @CircuitBreaker(name = "member-service", fallbackMethod = "getMemberFallback")
    public MemberDto getMember(Long memberId) {
        return memberClient.getMember(memberId);
    }

    public MemberDto getMemberFallback(Long memberId, Exception e) {
        log.warn("member-service 호출 실패! fallback 실행. memberId={}, error={}", memberId, e.getMessage());
        return new MemberDto(memberId, "알 수 없는 사용자", null, null);
    }
}

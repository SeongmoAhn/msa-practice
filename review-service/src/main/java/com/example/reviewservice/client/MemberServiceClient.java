package com.example.reviewservice.client;

import com.example.reviewservice.dto.MemberDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "member-service",
        url = "http://localhost:8081"
)
public interface MemberServiceClient {

    @GetMapping("/api/members/{id}")
    MemberDto getMember(@PathVariable("id") Long id);
}

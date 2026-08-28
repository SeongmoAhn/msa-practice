package com.example.memberservice.controller;

import com.example.memberservice.dto.MemberRequestDto;
import com.example.memberservice.dto.MemberResponseDto;
import com.example.memberservice.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {
    private final MemberService memberService;

    // 회원가입
    @PostMapping
    public ResponseEntity<MemberResponseDto> createMember(@RequestBody MemberRequestDto request) {
        MemberResponseDto response = memberService.createMember(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 회원 조회(ID)
    @GetMapping("/{id}")
    public ResponseEntity<MemberResponseDto> getMember(@PathVariable Long id) {
        MemberResponseDto response = memberService.getMember(id);
        return ResponseEntity.ok(response);
    }

    // 회원 조회(닉네임)
    @GetMapping
    public ResponseEntity<MemberResponseDto> getMemberByNickname(@RequestParam String nickname) {
        MemberResponseDto response = memberService.getMemberByNickname(nickname);
        return ResponseEntity.ok(response);
    }
}

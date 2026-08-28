package com.example.memberservice.service;

import com.example.memberservice.domain.Member;
import com.example.memberservice.dto.MemberRequestDto;
import com.example.memberservice.dto.MemberResponseDto;
import com.example.memberservice.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;

    public MemberResponseDto createMember(MemberRequestDto request) {
        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("Already exist Nickname: " + request.getNickname());
        }

        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Already exist Email: " + request.getEmail());
        }

        Member member = Member.builder()
                .nickname(request.getNickname())
                .email(request.getEmail())
                .password(request.getPassword())
                .build();

        Member saved = memberRepository.save(member);

        return MemberResponseDto.from(saved);
    }

    public MemberResponseDto getMember(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found ID: " + id));
        return MemberResponseDto.from(member);
    }

    public MemberResponseDto getMemberByNickname(String nickname) {
        Member member = memberRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("Not found Nickname: " + nickname));
        return MemberResponseDto.from(member);
    }
}

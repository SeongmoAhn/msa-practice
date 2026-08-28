package com.example.memberservice.repository;

import com.example.memberservice.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByNickname(String nickname);

    boolean existsByEmail(String email);

    Optional<Member> findByNickname(String nickname);
}

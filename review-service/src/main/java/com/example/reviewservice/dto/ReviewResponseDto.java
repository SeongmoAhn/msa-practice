package com.example.reviewservice.dto;

import com.example.reviewservice.domain.Review;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponseDto {
    private Long id;
    private String storeName;
    private Integer rating;
    private String content;
    private Long memberId;
    private String nickname;
    private LocalDateTime createAt;

    public static ReviewResponseDto from(Review review) {
        return ReviewResponseDto.builder()
                .id(review.getId())
                .storeName(review.getStoreName())
                .rating(review.getRating())
                .content(review.getContent())
                .memberId(review.getMemberId())
                .createAt(review.getCreatedAt())
                .build();
    }

    public static ReviewResponseDto from(Review review, MemberDto member) {
        return ReviewResponseDto.builder()
                .id(review.getId())
                .storeName(review.getStoreName())
                .rating(review.getRating())
                .content(review.getContent())
                .memberId(review.getMemberId())
                .nickname(member.getNickname())
                .createAt(review.getCreatedAt())
                .build();
    }
}

package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 双Token响应DTO
 * Access Token: 短期有效，用于访问资源
 * Refresh Token: 长期有效，用于刷新Access Token
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenPairDTO {
    /**
     * 访问令牌（短效）
     */
    private String accessToken;

    /**
     * 刷新令牌（长效）
     */
    private String refreshToken;

    /**
     * Access Token过期时间（秒）
     */
    private Long accessTokenExpiresIn;

    /**
     * Refresh Token过期时间（秒）
     */
    private Long refreshTokenExpiresIn;
}

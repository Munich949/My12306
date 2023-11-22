package com.dlnu.index12306.frameworks.starter.user.toolkit;

import com.alibaba.fastjson2.JSON;
import com.dlnu.index12306.frameworks.starter.user.core.UserInfoDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.dlnu.index12306.framework.starter.bases.constant.UserConstant.*;

/**
 * JWT 工具类
 */
@Slf4j
public final class JWTUtil {

    // Token 前缀
    public static final String TOKEN_PREFIX = "Bearer ";
    // Token 的发行者
    public static final String ISS = "index12306";
    // Token 的密钥
    public static final String SECRET = "SecretKey039245678901232039487623456783092349288901402967890140939827";
    // Token 的过期时间（一天的秒数）
    private static final long EXPIRATION = 86400L;

    /**
     * 生成用户 Token
     *
     * @param userInfo 用户信息
     * @return 用户访问 Token
     */
    public static String generateAccessToken(UserInfoDTO userInfo) {
        Map<String, Object> customerUserMap = new HashMap<>();
        customerUserMap.put(USER_ID_KEY, userInfo.getUserId());
        customerUserMap.put(USER_NAME_KEY, userInfo.getUsername());
        customerUserMap.put(REAL_NAME_KEY, userInfo.getRealName());
        String jwtToken = Jwts.builder()
                .signWith(SignatureAlgorithm.HS512, SECRET) // 使用HS512算法和密钥对 Token 签名
                .setIssuedAt(new Date())    // Token 的发行时间
                .setIssuer(ISS)     // Token 的发行者
                .setSubject(JSON.toJSONString(customerUserMap))     // Token 的主题
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION * 1000))    // Token 的过期时间
                .compact(); // 最终生成
        return TOKEN_PREFIX + jwtToken;
    }

    /**
     * 解析用户 Token
     *
     * @param jwtToken 用户访问 Token
     * @return 用户信息
     */
    public static UserInfoDTO parseJwtToken(String jwtToken) {
        if (StringUtils.hasText(jwtToken)) {
            String actualJwtToken = jwtToken.replace(TOKEN_PREFIX, "");
            try {
                // JWT 的声明部分
                Claims claims = Jwts.parser().setSigningKey(SECRET).parseClaimsJws(actualJwtToken).getBody();
                Date expiration = claims.getExpiration();
                // 未过期
                if (expiration.after(new Date())) {
                    String subject = claims.getSubject();
                    return JSON.parseObject(subject, UserInfoDTO.class);
                }
            } catch (ExpiredJwtException ignored) {
            } catch (Exception ex) {
                log.error("JWT Token解析失败，请检查", ex);
            }
        }
        return null;
    }
}
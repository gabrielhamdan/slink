package com.hamdan.slinkapi.infra.security;

import com.hamdan.slinkapi.entity.user.AnonUser;
import com.hamdan.slinkapi.entity.user.ApiUser;
import com.hamdan.slinkapi.entity.user.User;
import com.hamdan.slinkapi.infra.exception.ApiErrorException;
import com.hamdan.slinkapi.repository.ApiUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final int RATE_LIMIT = 100;

    public static final int RATE_LIMIT_DURATION = 60;

    private final ApiUserRepository apiUserRepository;

    private final PasswordEncoder passwordEncoder;

    private final StringRedisTemplate redisTemplate;

    public ApiKeyFilter(ApiUserRepository apiUserRepository, PasswordEncoder passwordEncoder, StringRedisTemplate redisTemplate) {
        this.apiUserRepository = apiUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var userName = request.getHeader("userName");
        var apiKey = ApiKey.get(request);

        var user = apiUserRepository.findByUserName(userName)
                .filter(u -> passwordEncoder.matches(apiKey, u.getApiKey()))
                .map(u -> (User) u)
                .orElse(new AnonUser(request.getRemoteAddr()));

        if (user instanceof AnonUser && hasReachedRateLimit(getIp(request))) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Máximo de requisições por minuto excedido.");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String getIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip != null && ip.contains(","))
            ip = ip.split(",")[0].trim();

        if (ip == null)
            ip = request.getRemoteAddr();

        return ip;
    }

    private boolean hasReachedRateLimit(String ip) {
        String key = "rate:" + ip;

        Long count = redisTemplate.opsForValue().increment(key);

        if (count == 1)
            redisTemplate.expire(key, Duration.ofSeconds(RATE_LIMIT_DURATION));

        return count > RATE_LIMIT;
    }

}

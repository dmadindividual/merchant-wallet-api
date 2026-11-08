package com.threeline.merchantwallet.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threeline.merchantwallet.common.context.RequestContext;
import com.threeline.merchantwallet.common.error.ErrorCode;
import com.threeline.merchantwallet.common.web.ProblemDetailFactory;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import com.threeline.merchantwallet.merchant.repository.MerchantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String MERCHANT_ATTRIBUTE = ApiKeyAuthenticationFilter.class.getName() + ".merchant";

    private static final String API_PREFIX = "/api/";
    private static final String REGISTRATION_PATH = "/api/v1/merchants";

    private final MerchantRepository merchants;
    private final ApiKeys apiKeys;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(MerchantRepository merchants, ApiKeys apiKeys, ObjectMapper objectMapper) {
        this.merchants = merchants;
        this.apiKeys = apiKeys;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean isApi = path.startsWith(API_PREFIX);
        boolean isRegistration = "POST".equalsIgnoreCase(request.getMethod()) && REGISTRATION_PATH.equals(path);
        return !isApi || isRegistration;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            ProblemDetailFactory.write(response, objectMapper, ErrorCode.UNAUTHORIZED,
                    "Send your API key in the " + HEADER + " header");
            return;
        }

        Optional<Merchant> merchant = merchants.findByApiKeyHash(apiKeys.hash(rawKey.trim()));
        if (merchant.isEmpty()) {
            ProblemDetailFactory.write(response, objectMapper, ErrorCode.UNAUTHORIZED, "API key is not valid");
            return;
        }
        if (!merchant.get().isActive()) {
            ProblemDetailFactory.write(response, objectMapper, ErrorCode.MERCHANT_NOT_ACTIVE,
                    "Merchant account is " + merchant.get().getStatus());
            return;
        }

        request.setAttribute(MERCHANT_ATTRIBUTE, merchant.get());
        RequestContext.setActor("merchant:" + merchant.get().getPublicId());
        chain.doFilter(request, response);
    }
}

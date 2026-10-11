package com.racetracker.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientAddressResolver {

    private final Set<String> trustedProxies;

    public ClientAddressResolver(@Value("${racetracker.security.trusted-proxies:}") String trustedProxyList) {
        trustedProxies = Arrays.stream(trustedProxyList.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || !trustedProxies.contains(remoteAddress)) {
            return remoteAddress == null ? "unknown" : remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        String[] chain = forwardedFor.split(",");
        for (int index = chain.length - 1; index >= 0; index--) {
            String candidate = chain[index].trim();
            if (!candidate.isEmpty() && !trustedProxies.contains(candidate)) {
                return candidate;
            }
        }
        return remoteAddress;
    }
}

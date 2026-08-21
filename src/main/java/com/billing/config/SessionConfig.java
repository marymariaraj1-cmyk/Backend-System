package com.billing.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class SessionConfig {

    private static final String SESSION_KEY = "SESSION_USER";

    public static void setSessionUser(Long clientId, String clientUsername, String shopName, String role) {
        HttpSession session = getCurrentSession();
        if (session != null) {
            session.setAttribute(SESSION_KEY, createSessionUser(clientId, clientUsername, shopName, role));
        }
    }

    public static SessionUser createSessionUser(Long clientId, String clientUsername, String shopName, String role) {
        return new SessionUser(clientId, clientUsername, shopName, role);
    }

    public static SessionUser getSessionUser() {
        HttpSession session = getCurrentSession();
        if (session != null) {
            Object obj = session.getAttribute(SESSION_KEY);
            if (obj instanceof SessionUser) {
                return (SessionUser) obj;
            }
        }
        return null;
    }

    public static Long getCurrentClientId() {
        SessionUser user = getSessionUser();
        return user != null ? user.getClientId() : null;
    }

    public static String getCurrentClientUsername() {
        SessionUser user = getSessionUser();
        return user != null ? user.getClientUsername() : null;
    }

    public static String getCurrentShopName() {
        SessionUser user = getSessionUser();
        return user != null ? user.getShopName() : null;
    }

    public static String getCurrentRole() {
        SessionUser user = getSessionUser();
        return user != null ? user.getRole() : null;
    }

    public static boolean hasRole(String role) {
        String currentRole = getCurrentRole();
        return currentRole != null && !currentRole.isEmpty() && currentRole.equals(role);
    }

    public static boolean isAdmin() {
        return hasRole("ROLE_ADMIN");
    }

    public static boolean isClient() {
        return hasRole("ROLE_CLIENT");
    }

    private static HttpSession getCurrentSession() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            return request.getSession(false);
        }
        return null;
    }
}

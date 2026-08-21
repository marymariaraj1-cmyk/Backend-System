package com.billing.controller;

import com.billing.config.SessionConfig;
import com.billing.config.SessionUser;
import com.billing.dto.ApiResponse;
import com.billing.dto.AuthRequest;
import com.billing.entity.ClientMaster;
import com.billing.service.ClientMasterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final ClientMasterService clientMasterService;

    @Autowired
    public AuthController(AuthenticationManager authenticationManager, ClientMasterService clientMasterService) {
        this.authenticationManager = authenticationManager;
        this.clientMasterService = clientMasterService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody AuthRequest request,
                                                                  HttpServletRequest httpRequest,
                                                                  HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            String role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(authority -> authority.getAuthority())
                    .orElse("");

            SessionUser sessionUser;
            if ("ROLE_ADMIN".equals(role)) {
                sessionUser = new SessionUser(null, "admin", "Administrator", role);
            } else {
                ClientMaster client = clientMasterService.getClientByUsername(request.getUsername());
                sessionUser = new SessionUser(client.getClientId(), client.getClientUsername(), client.getClientShopName(), role);
            }
            SessionConfig.setSessionUser(sessionUser.getClientId(), sessionUser.getClientUsername(), sessionUser.getShopName(), sessionUser.getRole());

            return ResponseEntity.ok(ApiResponse.success("Login successful", buildUserData(sessionUser)));
        } catch (org.springframework.security.core.AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Invalid username or password"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
                    .logout(httpRequest, httpResponse, authentication);
        }
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me() {
        SessionUser user = SessionConfig.getSessionUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Not authenticated"));
        }
        return ResponseEntity.ok(ApiResponse.success("OK", buildUserData(user)));
    }

    private Map<String, Object> buildUserData(SessionUser user) {
        Map<String, Object> data = new HashMap<>();
        data.put("clientId", user.getClientId());
        data.put("clientUsername", user.getClientUsername());
        data.put("shopName", user.getShopName());
        data.put("role", user.getRole());
        return data;
    }
}

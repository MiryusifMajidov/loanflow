package az.yusif.loanflow.shared.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Set;
import java.util.stream.Collectors;

public record CurrentUser(String id, String name, Set<String> roles) {

    public static CurrentUser from(JwtAuthenticationToken authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toUnmodifiableSet());
        String name = authentication.getToken().getClaimAsString("name");
        return new CurrentUser(authentication.getName(), name != null ? name : authentication.getName(), roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isStaff() {
        return hasRole(Roles.LOAN_OFFICER) || hasRole(Roles.RISK_MANAGER) || hasRole(Roles.ADMIN);
    }
}

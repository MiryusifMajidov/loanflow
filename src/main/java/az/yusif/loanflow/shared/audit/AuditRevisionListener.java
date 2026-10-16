package az.yusif.loanflow.shared.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class AuditRevisionListener implements RevisionListener {

    static final String SYSTEM = "system";

    @Override
    public void newRevision(Object revisionEntity) {
        ((AuditRevision) revisionEntity).setUsername(currentUsername());
    }

    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM;
        }
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String preferred = jwt.getToken().getClaimAsString("preferred_username");
            return preferred != null ? preferred : jwt.getName();
        }
        return authentication.getName();
    }
}

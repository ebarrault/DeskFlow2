package fr.portfolio.helpdesk.auth;

import fr.portfolio.helpdesk.common.ApiException;
import fr.portfolio.helpdesk.domain.UserRepository;
import fr.portfolio.helpdesk.security.AuthUser;
import fr.portfolio.helpdesk.user.UserView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "auth")
public class AuthController {

    private final AuthService auth;
    private final UserRepository users;

    public AuthController(AuthService auth, UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/register")
    public AuthDtos.LoginResponse register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return auth.login(req.email(), req.password());
    }

    @PostMapping("/2fa/verify")
    public AuthDtos.LoginResponse verify(@Valid @RequestBody AuthDtos.VerifyRequest req) {
        return auth.verifyTwoFactor(req.tempToken(), req.code());
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthUser me) {
        return users.findById(me.id()).map(UserView::of).orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
    }

    @PostMapping("/2fa/setup")
    public TotpService.Enrollment setup(@AuthenticationPrincipal AuthUser me) {
        return auth.setupTwoFactor(me.id());
    }

    @PostMapping("/2fa/enable")
    public AuthDtos.TwoFactorStatus enable(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AuthDtos.CodeRequest req) {
        return auth.enableTwoFactor(me.id(), req.code());
    }

    @PostMapping("/2fa/disable")
    public AuthDtos.TwoFactorStatus disable(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AuthDtos.CodeRequest req) {
        return auth.disableTwoFactor(me.id(), req.code());
    }
}

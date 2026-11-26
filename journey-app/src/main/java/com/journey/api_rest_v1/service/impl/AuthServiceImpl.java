package com.journey.api_rest_v1.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.config.AuthProperties;
import com.journey.api_rest_v1.domain.users.AccessToken;
import com.journey.api_rest_v1.domain.users.AuthIdentity;
import com.journey.api_rest_v1.domain.users.AuthProvider;
import com.journey.api_rest_v1.domain.users.PasswordResetToken;
import com.journey.api_rest_v1.domain.users.RefreshToken;
import com.journey.api_rest_v1.domain.users.User;
import com.journey.api_rest_v1.dto.auth.AuthResponseDto;
import com.journey.api_rest_v1.dto.auth.ChangePasswordRequest;
import com.journey.api_rest_v1.dto.auth.ForgotPasswordRequest;
import com.journey.api_rest_v1.dto.auth.LoginRequest;
import com.journey.api_rest_v1.dto.auth.RegisterRequest;
import com.journey.api_rest_v1.dto.auth.ResetPasswordRequest;
import com.journey.api_rest_v1.dto.auth.UserDto;
import com.journey.api_rest_v1.repository.AccessTokenRepository;
import com.journey.api_rest_v1.repository.AuthIdentityRepository;
import com.journey.api_rest_v1.repository.PasswordResetTokenRepository;
import com.journey.api_rest_v1.repository.RefreshTokenRepository;
import com.journey.api_rest_v1.repository.UserRepository;
import com.journey.api_rest_v1.security.RateLimiter;
import com.journey.api_rest_v1.security.TooManyRequestsException;
import com.journey.api_rest_v1.service.AuthService;
import com.journey.api_rest_v1.service.PasswordResetRequestedEvent;

/**
 * Default {@link AuthService}: bcrypt passwords, HS256 access JWTs, and rotating refresh tokens.
 *
 * <p>Refresh rotation: every refresh revokes the presented token and issues a replacement in the same family.
 * If an already-rotated token is presented again, someone else has a copy, so the whole family (every
 * refresh and access token of that sign-in) is revoked. A token rotated within the last few seconds is just
 * refused, so two browser tabs refreshing at once do not sign the user out.</p>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_LOGIN = "Wrong username or password";
    private static final String INVALID_SESSION = "Session expired, please sign in again";
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(10);
    private static final String SCOPE_USER = "user";
    private static final String SCOPE_ADMIN = "admin";
    private static final Duration RESET_TOKEN_LIFETIME = Duration.ofMinutes(30);
    /** At most this many reset emails per account per RESET_WINDOW, so the form cannot be used to spam. */
    private static final int MAX_RESETS_PER_WINDOW = 3;
    private static final Duration RESET_WINDOW = Duration.ofMinutes(15);
    /**
     * Failed sign-ins allowed per account (username or email) per 15 minutes, from any number of IPs. Stops
     * slow password guessing spread across many addresses; the per-IP limit is in RateLimitFilter.
     */
    private static final RateLimiter.Rule FAILED_LOGINS_PER_ACCOUNT =
            new RateLimiter.Rule("login-account", 10, Duration.ofMinutes(15));
    private static final String INVALID_RESET = "This reset link is invalid or has expired. Please request a new one.";

    private final UserRepository userRepository;
    private final AuthIdentityRepository identityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenRepository accessTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final ApplicationEventPublisher events;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    /** Compared against when the user does not exist, so a missing user takes as long as a wrong password. */
    private final String dummyHash;

    /** Wires the repositories, password encoder, and JWT encoder. */
    public AuthServiceImpl(UserRepository userRepository,
            AuthIdentityRepository identityRepository,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenRepository accessTokenRepository,
            PasswordResetTokenRepository resetTokenRepository,
            ApplicationEventPublisher events,
            PasswordEncoder passwordEncoder,
            RateLimiter rateLimiter,
            JwtEncoder jwtEncoder,
            AuthProperties properties) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenRepository = accessTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.events = events;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode(TokenHashing.newToken());
    }

    @Override
    @Transactional
    public Session register(RegisterRequest request, ClientInfo client) {
        checkPasswordLength(request.password());
        String username = normalize(request.username());
        if (identityRepository.findByProviderAndProviderSubject(AuthProvider.PASSWORD, username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is taken");
        }
        String email = blankToNull(request.email());
        if (email != null && userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");
        }

        User user = new User();
        String displayName = blankToNull(request.display_name());
        user.setDisplayName(displayName != null ? displayName.trim() : request.username().trim());
        user.setEmail(email == null ? null : email.trim());
        user.setLastLoginAt(now());
        user = userRepository.save(user);

        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthProvider.PASSWORD);
        identity.setProviderSubject(username);
        identity.setEmail(user.getEmail());
        identity.setPasswordHash(passwordEncoder.encode(request.password()));
        identity.setPasswordUpdatedAt(now());
        identity.setLastUsedAt(now());
        identity = identityRepository.save(identity);

        return issueSession(user, identity, UUID.randomUUID(), client, null);
    }

    @Override
    @Transactional
    public Session login(LoginRequest request, ClientInfo client) {
        // Keyed by what was typed (also for unknown accounts), so the limit says nothing about which exist.
        String accountKey = request.login() == null ? "" : request.login().trim().toLowerCase(Locale.ROOT);
        RateLimiter.Decision allowed = rateLimiter.check(FAILED_LOGINS_PER_ACCOUNT, accountKey);
        if (!allowed.allowed()) {
            throw new TooManyRequestsException("Too many failed sign-in attempts for this account. Try again in "
                    + Math.max(1, (allowed.retryAfterSeconds() + 59) / 60) + " minute(s), or reset your password.",
                    allowed.retryAfterSeconds());
        }
        AuthIdentity identity = findPasswordIdentity(request.login());
        if (identity == null || identity.getPasswordHash() == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            rateLimiter.tryConsume(FAILED_LOGINS_PER_ACCOUNT, accountKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        }
        if (!passwordEncoder.matches(request.password(), identity.getPasswordHash())) {
            rateLimiter.tryConsume(FAILED_LOGINS_PER_ACCOUNT, accountKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        }
        User user = identity.getUser();
        if (user.getDisabledAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is disabled");
        }
        user.setLastLoginAt(now());
        identity.setLastUsedAt(now());
        return issueSession(user, identity, UUID.randomUUID(), client, null);
    }

    // noRollbackFor: revoking a stolen token's family must stick even though the request then fails with 401.
    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Session refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_SESSION);
        }
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(TokenHashing.sha256(rawRefreshToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_SESSION));
        OffsetDateTime now = now();

        if (current.getRevokedAt() != null) {
            boolean justRotated = "rotated".equals(current.getRevokedReason())
                    && current.getRevokedAt().isAfter(now.minus(ROTATION_GRACE));
            if (!justRotated) {
                revokeFamily(current.getFamilyId(), "reuse_detected");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_SESSION);
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_SESSION);
        }
        User user = userRepository.findById(current.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_SESSION));
        if (user.getDisabledAt() != null) {
            revokeFamily(current.getFamilyId(), "disabled");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_SESSION);
        }

        current.setRevokedAt(now);
        current.setRevokedReason("rotated");
        current.setLastUsedAt(now);
        AuthIdentity identity = current.getIdentityId() == null ? null
                : identityRepository.findById(current.getIdentityId()).orElse(null);
        return issueSession(user, identity, current.getFamilyId(), client, current);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenHashing.sha256(rawRefreshToken))
                .ifPresent(token -> revokeFamily(token.getFamilyId(), "logout"));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
        String username = identityRepository.findByUser_IdAndProvider(userId, AuthProvider.PASSWORD)
                .map(AuthIdentity::getProviderSubject).orElse(null);
        return toUserDto(user, username);
    }

    @Override
    @Transactional
    public Session changePassword(UUID userId, ChangePasswordRequest request, ClientInfo client) {
        checkPasswordLength(request.new_password());
        AuthIdentity identity = identityRepository.findByUser_IdAndProvider(userId, AuthProvider.PASSWORD)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "This account has no password"));
        // 400 rather than 401: a 401 would make clients think the session ended and sign the user out.
        if (!passwordEncoder.matches(request.current_password(), identity.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is wrong");
        }
        if (passwordEncoder.matches(request.new_password(), identity.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The new password must be different");
        }
        setPassword(identity, request.new_password(), "password_changed");
        identity.setLastUsedAt(now());
        return issueSession(identity.getUser(), identity, UUID.randomUUID(), client, null);
    }

    @Override
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request, ClientInfo client) {
        OffsetDateTime now = now();
        userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(user -> user.getDisabledAt() == null)
                .flatMap(user -> identityRepository.findByUser_IdAndProvider(user.getId(), AuthProvider.PASSWORD))
                .filter(identity -> resetTokenRepository.countByUserIdAndCreatedAtAfter(
                        identity.getUser().getId(), now.minus(RESET_WINDOW)) < MAX_RESETS_PER_WINDOW)
                .ifPresent(identity -> {
                    User user = identity.getUser();
                    resetTokenRepository.invalidateAllForUser(user.getId(), now);
                    String rawToken = TokenHashing.newToken();
                    PasswordResetToken token = new PasswordResetToken();
                    token.setUserId(user.getId());
                    token.setTokenHash(TokenHashing.sha256(rawToken));
                    token.setExpiresAt(now.plus(RESET_TOKEN_LIFETIME));
                    token.setRequestedIp(client.ipAddress());
                    resetTokenRepository.save(token);
                    events.publishEvent(new PasswordResetRequestedEvent(user.getEmail(), user.getDisplayName(),
                            rawToken, RESET_TOKEN_LIFETIME.toMinutes()));
                });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        checkPasswordLength(request.new_password());
        OffsetDateTime now = now();
        PasswordResetToken token = resetTokenRepository.findByTokenHashForUpdate(TokenHashing.sha256(request.token()))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_RESET));
        AuthIdentity identity = identityRepository.findByUser_IdAndProvider(token.getUserId(), AuthProvider.PASSWORD)
                .filter(i -> i.getUser().getDisabledAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_RESET));
        token.setUsedAt(now);
        setPassword(identity, request.new_password(), "password_reset");
    }

    /** Stores a new password hash and signs out every session; unused reset links stop working. */
    private void setPassword(AuthIdentity identity, String newPassword, String reason) {
        OffsetDateTime now = now();
        UUID userId = identity.getUser().getId();
        identity.setPasswordHash(passwordEncoder.encode(newPassword));
        identity.setPasswordUpdatedAt(now);
        resetTokenRepository.invalidateAllForUser(userId, now);
        refreshTokenRepository.revokeAllForUser(userId, now, reason);
        accessTokenRepository.revokeAllForUser(userId, now);
    }

    // ----------------------------
    // Sessions and tokens
    // ----------------------------

    /** Creates a refresh token (replacing {@code previous}, if any) and an access JWT for the user. */
    private Session issueSession(User user, AuthIdentity identity, UUID familyId, ClientInfo client,
            RefreshToken previous) {
        OffsetDateTime now = now();
        Duration refreshLifetime = Duration.ofDays(properties.refreshTokenDays());
        Duration accessLifetime = Duration.ofMinutes(properties.accessTokenMinutes());

        String rawRefreshToken = TokenHashing.newToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setIdentityId(identity == null ? null : identity.getId());
        refreshToken.setTokenHash(TokenHashing.sha256(rawRefreshToken));
        refreshToken.setFamilyId(familyId);
        refreshToken.setExpiresAt(now.plus(refreshLifetime));
        refreshToken.setUserAgent(truncate(client.userAgent(), 512));
        refreshToken.setIpAddress(client.ipAddress());
        refreshToken.setLastUsedAt(now);
        refreshToken = refreshTokenRepository.save(refreshToken);
        if (previous != null) {
            previous.setReplacedById(refreshToken.getId());
        }

        boolean admin = user.isAdmin();
        String scope = admin ? SCOPE_USER + " " + SCOPE_ADMIN : SCOPE_USER;
        String username = identity != null && identity.getProvider() == AuthProvider.PASSWORD
                ? identity.getProviderSubject() : null;
        Instant issuedAt = now.toInstant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(accessLifetime))
                .claim("scope", scope)
                .claim("name", user.getDisplayName());
        if (username != null) {
            claims.claim("username", username);
        }
        String jwt = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();

        AccessToken accessToken = new AccessToken();
        accessToken.setUserId(user.getId());
        accessToken.setRefreshTokenId(refreshToken.getId());
        accessToken.setTokenHash(TokenHashing.sha256(jwt));
        accessToken.setScopes(scope.split(" "));
        accessToken.setExpiresAt(now.plus(accessLifetime));
        accessTokenRepository.save(accessToken);

        AuthResponseDto response = new AuthResponseDto(jwt, "Bearer", accessLifetime.toSeconds(),
                toUserDto(user, username));
        return new Session(response, rawRefreshToken, refreshLifetime);
    }

    /** Revokes every refresh and access token of a sign-in (runs in the caller's transaction). */
    private void revokeFamily(UUID familyId, String reason) {
        OffsetDateTime now = now();
        refreshTokenRepository.revokeFamily(familyId, now, reason);
        accessTokenRepository.revokeFamily(familyId, now);
    }

    // ----------------------------
    // Helpers
    // ----------------------------

    private AuthIdentity findPasswordIdentity(String login) {
        String value = login == null ? "" : login.trim();
        if (value.contains("@")) {
            return userRepository.findByEmailIgnoreCase(value)
                    .flatMap(user -> identityRepository.findByUser_IdAndProvider(user.getId(), AuthProvider.PASSWORD))
                    .orElse(null);
        }
        return identityRepository.findByProviderAndProviderSubject(AuthProvider.PASSWORD, normalize(value)).orElse(null);
    }

    private static void checkPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 bytes");
        }
    }

    private static UserDto toUserDto(User user, String username) {
        return new UserDto(user.getId().toString(), username, user.getDisplayName(), user.getEmail(), user.isAdmin());
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}

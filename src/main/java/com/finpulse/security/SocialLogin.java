package com.finpulse.security;

import com.finpulse.dto.AuthResponse;
import com.finpulse.service.AccountRecovery;
import com.finpulse.service.SocialAccounts;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class SocialLogin {
    private static final String CHALLENGE = "veltro_oauth_challenge";
    private final SocialAccounts accounts;
    private final String frontend;
    private final String backend;
    private final List<ClientRegistration> providers = new ArrayList<>();
    private final Map<String, Pending> exchanges = new ConcurrentHashMap<>();

    private record Pending(String challenge, SocialAccounts.Identity identity, Instant expires) {}

    public SocialLogin(SocialAccounts accounts, @Value("${app.frontend-url}") String frontend,
                       @Value("${app.backend-url}") String backend,
                       @Value("${app.oauth.google-id}") String googleId, @Value("${app.oauth.google-secret}") String googleSecret,
                       @Value("${app.oauth.apple-id}") String appleId, @Value("${app.oauth.apple-secret}") String appleSecret) {
        this.accounts = accounts;
        this.frontend = frontend.replaceAll("/+$", "");
        this.backend = backend.replaceAll("/+$", "");
        if (!googleId.isBlank() && !googleSecret.isBlank()) {
            providers.add(CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId(googleId).clientSecret(googleSecret)
                    .redirectUri(this.backend + "/login/oauth2/code/google").build());
        }
        if (!appleId.isBlank() && !appleSecret.isBlank() && this.backend.startsWith("https://")) {
            providers.add(ClientRegistration.withRegistrationId("apple").clientName("Apple")
                    .clientId(appleId).clientSecret(appleSecret)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationUri("https://appleid.apple.com/auth/authorize")
                    .tokenUri("https://appleid.apple.com/auth/token")
                    .issuerUri("https://appleid.apple.com")
                    .jwkSetUri("https://appleid.apple.com/auth/keys")
                    .redirectUri(this.backend + "/login/oauth2/code/apple")
                    .scope("openid", "email").build());
        }
    }

    @Bean
    ClientRegistrationRepository socialRegistrations() {
        return id -> providers.stream().filter(provider -> provider.getRegistrationId().equals(id)).findFirst().orElse(null);
    }

    public List<String> enabledProviders() {
        return providers.stream().map(ClientRegistration::getRegistrationId).toList();
    }

    public void configure(HttpSecurity http, ClientRegistrationRepository registrations) throws Exception {
        if (providers.isEmpty()) return;
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        http.oauth2Login(login -> login.clientRegistrationRepository(registrations)
                .authorizedClientRepository(new OAuth2AuthorizedClientRepository() {
                    @Override
                    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String provider, Authentication authentication,
                                                                                    HttpServletRequest request) {
                        return null;
                    }
                    @Override
                    public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication authentication,
                                                     HttpServletRequest request, HttpServletResponse response) {
                        // Veltro uses the verified identity only.
                    }
                    @Override
                    public void removeAuthorizedClient(String provider, Authentication authentication,
                                                       HttpServletRequest request, HttpServletResponse response) {}
                })
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(new OAuth2AuthorizationRequestResolver() {
                    @Override
                    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                        return prepare(request, resolver.resolve(request));
                    }
                    @Override
                    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String client) {
                        return prepare(request, resolver.resolve(request, client));
                    }
                }))
                .successHandler(this::success)
                .failureHandler((request, response, error) -> fail(request, response)));
    }

    private OAuth2AuthorizationRequest prepare(HttpServletRequest request, OAuth2AuthorizationRequest authorization) {
        if (authorization == null) return null;
        String challenge = request.getParameter("challenge");
        if (challenge == null || !challenge.matches("[a-f0-9]{64}")) return null;
        request.getSession().setAttribute(CHALLENGE, challenge);
        if (!"apple".equals(authorization.getAttribute("registration_id"))) return authorization;
        // Keep OpenID scope in Spring's request model so it validates the ID token and nonce.
        // Apple accepts only its documented email/name scopes on the authorization URL.
        var uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(authorization.getAuthorizationRequestUri())
                .replaceQueryParam("scope", "email").replaceQueryParam("response_mode", "form_post")
                .build(true).toUriString();
        return OAuth2AuthorizationRequest.from(authorization).authorizationRequestUri(uri).build();
    }

    private void success(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        var session = request.getSession(false);
        String challenge = session == null ? null : (String) session.getAttribute(CHALLENGE);
        if (!(authentication.getPrincipal() instanceof OidcUser user) || challenge == null
                || !"true".equalsIgnoreCase(String.valueOf(user.getClaims().get("email_verified")))
                || user.getEmail() == null || user.getEmail().isBlank() || user.getEmail().length() > 150) {
            fail(request, response);
            return;
        }
        String provider = ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();
        String name = user.getFullName();
        if (name == null || name.isBlank()) name = "Veltro member";
        if (name.length() > 120) name = name.substring(0, 120);
        var identity = new SocialAccounts.Identity(provider, user.getSubject(), user.getEmail().strip().toLowerCase(Locale.ROOT), name);
        String code = AccountRecovery.randomToken();
        synchronized (exchanges) {
            Instant now = Instant.now();
            exchanges.values().removeIf(item -> !item.expires().isAfter(now));
            if (exchanges.size() >= 1024) {
                fail(request, response);
                return;
            }
            exchanges.put(code, new Pending(challenge, identity, now.plusSeconds(180)));
        }
        session.invalidate();
        redirect(response, "#code=" + code);
    }

    public AuthResponse exchange(String code, String verifier, String password) {
        var pending = exchanges.get(code);
        if (pending == null || !pending.expires().isAfter(Instant.now())
                || !pending.challenge().equals(AccountRecovery.digest(verifier)) || !exchanges.remove(code, pending)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sign-in expired. Start again from the sign-in page.");
        }
        try {
            return accounts.signIn(pending.identity(), password);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) exchanges.putIfAbsent(code, pending);
            throw exception;
        }
    }

    private void fail(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        redirect(response, "#error=provider");
    }

    private void redirect(HttpServletResponse response, String fragment) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.sendRedirect(frontend + "/auth?mode=social" + fragment);
    }
}

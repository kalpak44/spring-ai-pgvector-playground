package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import online.pavelusanli.config.props.GoogleProps;
import online.pavelusanli.model.dto.GoogleTokenResponse;
import online.pavelusanli.model.dto.GoogleUserInfo;
import online.pavelusanli.model.entity.UserGoogleToken;
import online.pavelusanli.repo.UserGoogleTokenRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private static final String AUTH_URL     = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL    = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private static final String SCOPE_GMAIL    = "https://www.googleapis.com/auth/gmail.modify";
    private static final String SCOPE_CALENDAR = "https://www.googleapis.com/auth/calendar";
    private static final String SCOPE_EMAIL    = "email";

    private static final int EXPIRY_BUFFER_MINUTES = 5;

    private static final String PARAM_CLIENT_ID     = "client_id";
    private static final String PARAM_CLIENT_SECRET = "client_secret";
    private static final String PARAM_REDIRECT_URI  = "redirect_uri";
    private static final String PARAM_GRANT_TYPE    = "grant_type";

    private final GoogleProps googleProps;
    private final UserGoogleTokenRepository tokenRepository;
    private final RestClient restClient = RestClient.create();

    public String buildAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam(PARAM_CLIENT_ID, googleProps.clientId())
                .queryParam(PARAM_REDIRECT_URI, googleProps.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", List.of(SCOPE_GMAIL, SCOPE_CALENDAR, SCOPE_EMAIL)))
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .toUriString();
    }

    public void exchangeCodeAndStore(String code, Long userId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add(PARAM_CLIENT_ID, googleProps.clientId());
        form.add(PARAM_CLIENT_SECRET, googleProps.clientSecret());
        form.add(PARAM_REDIRECT_URI, googleProps.redirectUri());
        form.add(PARAM_GRANT_TYPE, "authorization_code");

        GoogleTokenResponse response = postTokenForm(form);
        saveTokens(userId, response);
    }

    public String getValidAccessToken(Long userId) {
        UserGoogleToken token = tokenRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("No Google token for user " + userId));

        if (isExpiringSoon(token)) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("refresh_token", token.getRefreshToken());
            form.add(PARAM_CLIENT_ID, googleProps.clientId());
            form.add(PARAM_CLIENT_SECRET, googleProps.clientSecret());
            form.add(PARAM_GRANT_TYPE, "refresh_token");

            GoogleTokenResponse response = postTokenForm(form);
            token.setAccessToken(response.accessToken());
            if (response.expiresIn() != null) {
                token.setTokenExpiry(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(response.expiresIn()));
            }
            tokenRepository.save(token);
        }

        return token.getAccessToken();
    }

    public boolean hasRequiredScopes(Long userId) {
        return tokenRepository.findById(userId)
                .map(token -> {
                    String scopes = token.getGrantedScopes();
                    return scopes != null
                            && scopes.contains(SCOPE_GMAIL)
                            && scopes.contains(SCOPE_CALENDAR);
                })
                .orElse(false);
    }

    public Optional<UserGoogleToken> findToken(Long userId) {
        return tokenRepository.findById(userId);
    }

    public void disconnect(Long userId) {
        tokenRepository.deleteById(userId);
    }

    private GoogleTokenResponse postTokenForm(MultiValueMap<String, String> form) {
        return restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(GoogleTokenResponse.class);
    }

    private void saveTokens(Long userId, GoogleTokenResponse response) {
        UserGoogleToken token = tokenRepository.findById(userId).orElse(new UserGoogleToken());
        token.setUserId(userId);
        token.setAccessToken(response.accessToken());
        if (response.refreshToken() != null) token.setRefreshToken(response.refreshToken());
        token.setTokenExpiry(response.expiresIn() != null
                ? LocalDateTime.now(ZoneOffset.UTC).plusSeconds(response.expiresIn()) : null);
        token.setGoogleEmail(fetchGoogleEmail(response.accessToken()));
        token.setGrantedScopes(response.scope());
        token.setConnectedAt(LocalDateTime.now(ZoneOffset.UTC));
        tokenRepository.save(token);
    }

    private String fetchGoogleEmail(String accessToken) {
        try {
            GoogleUserInfo info = restClient.get()
                    .uri(USERINFO_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleUserInfo.class);
            return info != null ? info.email() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isExpiringSoon(UserGoogleToken token) {
        return token.getTokenExpiry() != null
                && token.getTokenExpiry().isBefore(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(EXPIRY_BUFFER_MINUTES));
    }
}
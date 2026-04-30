package com.iflytek.skillhub.auth.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Authenticates WeCom browser authorization codes and converts them into SkillHub sessions.
 */
@Service
public class WeComAuthService {

    public static final String PROVIDER_CODE = "wecom";
    private static final long TOKEN_REFRESH_SKEW_SECONDS = 60;

    private final WeComProperties properties;
    private final IdentityBindingService identityBindingService;
    private final RestClient restClient;

    private volatile String accessToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;

    public WeComAuthService(WeComProperties properties,
                            IdentityBindingService identityBindingService,
                            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.identityBindingService = identityBindingService;
        this.restClient = restClientBuilder.build();
    }

    public WeComLoginConfig getLoginConfig() {
        boolean configured = properties.isConfigured();
        return new WeComLoginConfig(
                configured,
                configured ? properties.getCorpId() : "",
                configured ? properties.getAgentId() : "",
                configured ? properties.getRedirectUri() : ""
        );
    }

    public Map<String, String> buildAuthorizeUrl(String state) {
        ensureConfigured();
        if (!StringUtils.hasText(state)) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.wecom.stateRequired");
        }

        String url = properties.getAuthorizeBaseUrl()
                + "?appid=" + encode(properties.getCorpId())
                + "&agentid=" + encode(properties.getAgentId())
                + "&redirect_uri=" + encode(properties.getRedirectUri())
                + "&state=" + encode(state.trim());

        return Map.of("url", url);
    }

    public PlatformPrincipal loginByCode(String code) {
        ensureConfigured();
        if (!StringUtils.hasText(code)) {
            throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.wecom.codeRequired");
        }

        WeComUserInfo userInfo = fetchUserInfo(code.trim());
        Map<String, Object> extra = new LinkedHashMap<>();
        if (StringUtils.hasText(userInfo.avatarUrl())) {
            extra.put("avatar_url", userInfo.avatarUrl());
        }
        if (StringUtils.hasText(userInfo.departmentName())) {
            extra.put("department", userInfo.departmentName());
        }
        if (StringUtils.hasText(userInfo.alias())) {
            extra.put("alias", userInfo.alias());
        }

        OAuthClaims claims = new OAuthClaims(
                PROVIDER_CODE,
                userInfo.userId(),
                userInfo.email(),
                StringUtils.hasText(userInfo.email()),
                userInfo.displayName(),
                extra
        );
        return identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE);
    }

    private WeComUserInfo fetchUserInfo(String code) {
        String token = getAccessToken();
        JsonNode response = getJson(properties.getUserInfoUrl()
                + "?access_token=" + encode(token)
                + "&code=" + encode(code));
        ensureSuccess(response, "error.auth.wecom.fetchIdentityFailed");

        String userId = getRequiredText(response, "UserId", "error.auth.wecom.userIdMissing");
        JsonNode detail = getJson(properties.getUserDetailUrl()
                + "?access_token=" + encode(token)
                + "&userid=" + encode(userId));
        ensureSuccess(detail, "error.auth.wecom.fetchUserFailed");

        String displayName = firstNonBlank(
                getOptionalText(detail, "name"),
                getOptionalText(detail, "alias"),
                userId
        );
        String email = firstNonBlank(
                getOptionalText(detail, "biz_mail"),
                getOptionalText(detail, "email")
        );

        return new WeComUserInfo(
                userId,
                displayName,
                email,
                getOptionalText(detail, "avatar"),
                getOptionalText(detail, "alias"),
                resolveDepartmentName(detail, token)
        );
    }

    private String getAccessToken() {
        Instant now = Instant.now();
        if (StringUtils.hasText(accessToken) && now.isBefore(accessTokenExpiresAt)) {
            return accessToken;
        }

        synchronized (this) {
            now = Instant.now();
            if (StringUtils.hasText(accessToken) && now.isBefore(accessTokenExpiresAt)) {
                return accessToken;
            }

            JsonNode response = getJson(properties.getAccessTokenUrl()
                    + "?corpid=" + encode(properties.getCorpId())
                    + "&corpsecret=" + encode(properties.getAgentSecret()));
            ensureSuccess(response, "error.auth.wecom.fetchTokenFailed");

            String token = getRequiredText(response, "access_token", "error.auth.wecom.tokenMissing");
            long expiresIn = response.path("expires_in").asLong(7200);
            accessToken = token;
            accessTokenExpiresAt = Instant.now().plusSeconds(Math.max(0, expiresIn - TOKEN_REFRESH_SKEW_SECONDS));
            return token;
        }
    }

    private JsonNode getJson(String url) {
        try {
            return restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (AuthFlowException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthFlowException(HttpStatus.BAD_GATEWAY, "error.auth.wecom.remoteCallFailed");
        }
    }

    private void ensureSuccess(JsonNode response, String fallbackMessageCode) {
        if (response == null) {
            throw new AuthFlowException(HttpStatus.BAD_GATEWAY, fallbackMessageCode);
        }
        int errcode = response.path("errcode").asInt(0);
        if (errcode == 0) {
            return;
        }
        String errmsg = getOptionalText(response, "errmsg");
        if (StringUtils.hasText(errmsg)) {
            throw new AuthFlowException(HttpStatus.BAD_GATEWAY, fallbackMessageCode + ".withReason", errmsg);
        }
        throw new AuthFlowException(HttpStatus.BAD_GATEWAY, fallbackMessageCode);
    }

    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.wecom.notConfigured");
        }
    }

    private String resolveDepartmentName(JsonNode detail, String token) {
        JsonNode departmentNode = detail.path("department");
        if (!departmentNode.isArray() || departmentNode.isEmpty()) {
            return "";
        }
        String departmentId = departmentNode.get(0).asText("");
        if (!StringUtils.hasText(departmentId)) {
            return "";
        }

        JsonNode response = getJson(properties.getDepartmentListUrl() + "?access_token=" + encode(token));
        ensureSuccess(response, "error.auth.wecom.fetchDepartmentFailed");
        JsonNode departments = response.path("department");
        if (!departments.isArray()) {
            return "";
        }
        for (JsonNode department : departments) {
            if (departmentId.equals(department.path("id").asText(""))) {
                return getOptionalText(department, "name");
            }
        }
        return "";
    }

    private String getRequiredText(JsonNode node, String field, String messageCode) {
        String value = getOptionalText(node, field);
        if (!StringUtils.hasText(value)) {
            throw new AuthFlowException(HttpStatus.BAD_GATEWAY, messageCode);
        }
        return value;
    }

    private String getOptionalText(JsonNode node, String field) {
        JsonNode valueNode = node.path(field);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return "";
        }
        String value = valueNode.asText("");
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record WeComUserInfo(
            String userId,
            String displayName,
            String email,
            String avatarUrl,
            String alias,
            String departmentName
    ) {
    }
}

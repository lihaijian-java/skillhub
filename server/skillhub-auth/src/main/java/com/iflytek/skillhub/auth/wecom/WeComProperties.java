package com.iflytek.skillhub.auth.wecom;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Configuration for WeCom self-built application browser login.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.auth.wecom")
public class WeComProperties {

    private boolean enabled = false;
    private String corpId;
    private String agentId;
    private String agentSecret;
    private String redirectUri;
    private String authorizeBaseUrl = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";
    private String accessTokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private String userInfoUrl = "https://qyapi.weixin.qq.com/cgi-bin/user/getuserinfo";
    private String userDetailUrl = "https://qyapi.weixin.qq.com/cgi-bin/user/get";
    private String departmentListUrl = "https://qyapi.weixin.qq.com/cgi-bin/department/list";

    public boolean isConfigured() {
        return enabled
                && StringUtils.hasText(corpId)
                && StringUtils.hasText(agentId)
                && StringUtils.hasText(agentSecret)
                && StringUtils.hasText(redirectUri);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCorpId() {
        return corpId;
    }

    public void setCorpId(String corpId) {
        this.corpId = corpId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentSecret() {
        return agentSecret;
    }

    public void setAgentSecret(String agentSecret) {
        this.agentSecret = agentSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getAuthorizeBaseUrl() {
        return authorizeBaseUrl;
    }

    public void setAuthorizeBaseUrl(String authorizeBaseUrl) {
        this.authorizeBaseUrl = authorizeBaseUrl;
    }

    public String getAccessTokenUrl() {
        return accessTokenUrl;
    }

    public void setAccessTokenUrl(String accessTokenUrl) {
        this.accessTokenUrl = accessTokenUrl;
    }

    public String getUserInfoUrl() {
        return userInfoUrl;
    }

    public void setUserInfoUrl(String userInfoUrl) {
        this.userInfoUrl = userInfoUrl;
    }

    public String getUserDetailUrl() {
        return userDetailUrl;
    }

    public void setUserDetailUrl(String userDetailUrl) {
        this.userDetailUrl = userDetailUrl;
    }

    public String getDepartmentListUrl() {
        return departmentListUrl;
    }

    public void setDepartmentListUrl(String departmentListUrl) {
        this.departmentListUrl = departmentListUrl;
    }
}

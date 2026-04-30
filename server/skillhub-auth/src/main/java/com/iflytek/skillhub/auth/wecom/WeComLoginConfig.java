package com.iflytek.skillhub.auth.wecom;

/**
 * Public WeCom login settings safe for browser rendering.
 */
public record WeComLoginConfig(
        boolean enabled,
        String corpId,
        String agentId,
        String redirectUri
) {
}

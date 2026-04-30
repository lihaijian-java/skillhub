package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.auth.wecom.WeComAuthService;
import com.iflytek.skillhub.auth.wecom.WeComLoginConfig;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.AuthMeResponse;
import com.iflytek.skillhub.dto.WeComLoginRequest;
import com.iflytek.skillhub.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WeCom browser login endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth/wecom")
public class WeComAuthController extends BaseApiController {

    private final WeComAuthService weComAuthService;
    private final PlatformSessionService platformSessionService;

    public WeComAuthController(ApiResponseFactory responseFactory,
                               WeComAuthService weComAuthService,
                               PlatformSessionService platformSessionService) {
        super(responseFactory);
        this.weComAuthService = weComAuthService;
        this.platformSessionService = platformSessionService;
    }

    @GetMapping("/config")
    public ApiResponse<WeComLoginConfig> config() {
        return ok("response.success.read", weComAuthService.getLoginConfig());
    }

    @GetMapping("/authorize-url")
    public ApiResponse<Map<String, String>> authorizeUrl(@RequestParam String state) {
        return ok("response.success.read", weComAuthService.buildAuthorizeUrl(state));
    }

    @PostMapping("/login")
    @RateLimit(category = "auth-wecom-login", authenticated = 20, anonymous = 10, windowSeconds = 60)
    public ApiResponse<AuthMeResponse> login(@Valid @RequestBody WeComLoginRequest request,
                                             HttpServletRequest httpRequest) {
        PlatformPrincipal principal = weComAuthService.loginByCode(request.code());
        platformSessionService.establishSession(principal, httpRequest);
        return ok("response.success.read", AuthMeResponse.from(principal));
    }
}

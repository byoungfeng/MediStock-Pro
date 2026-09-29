package com.medistock.pro.modules.system;

import cn.dev33.satoken.stp.StpUtil;
import com.medistock.pro.modules.system.entity.OperationLog;
import com.medistock.pro.modules.system.entity.SysUser;
import com.medistock.pro.modules.system.mapper.OperationLogMapper;
import com.medistock.pro.modules.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 操作日志拦截器: 记录 /api/** 的写操作 (非 GET), 覆盖全部业务模块无需逐点注解
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogInterceptor implements HandlerInterceptor {

    private final OperationLogMapper operationLogMapper;
    private final SysUserMapper sysUserMapper;
    private final ApiMetricsCollector metricsCollector;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("__startNs", System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 接口监控 (P059): 全方法采集, 含 GET
        Object startNs = request.getAttribute("__startNs");
        if (startNs instanceof Long start) {
            metricsCollector.record(request.getMethod(), request.getRequestURI(),
                    response.getStatus(), (System.nanoTime() - start) / 1_000_000);
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return;
        }
        try {
            OperationLog logEntry = new OperationLog();
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId != null) {
                long uid = Long.parseLong(loginId.toString());
                logEntry.setUserId(uid);
                SysUser u = sysUserMapper.selectById(uid);
                logEntry.setUsername(u != null ? u.getUsername() : null);
            }
            String uri = request.getRequestURI();
            logEntry.setMethod(request.getMethod());
            logEntry.setUri(uri.length() > 255 ? uri.substring(0, 255) : uri);
            logEntry.setIp(clientIp(request));
            // /api/v1/{module}/.../{businessId}/{action...}
            String[] seg = uri.split("/");
            if (seg.length > 3) {
                logEntry.setModule(seg[3]);
            }
            StringBuilder action = new StringBuilder(request.getMethod());
            for (int i = seg.length - 1; i > 3; i--) {
                if (seg[i].matches("\\d+")) {
                    logEntry.setBusinessId(seg[i]);
                } else {
                    action.append(' ').append(seg[i]);
                }
            }
            logEntry.setAction(action.toString());
            logEntry.setStatus(ex == null && response.getStatus() < 400 ? 1 : 0);
            if (ex != null) {
                String msg = ex.getMessage();
                logEntry.setErrorMsg(msg != null && msg.length() > 1000 ? msg.substring(0, 1000) : msg);
            }
            operationLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 日志失败不影响业务
            log.warn("操作日志写入失败: {}", e.getMessage());
        }
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

package com.jxl.ai.intelliconf.common.biz.portal;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.Optional;

public final class PortalCommitteeContext {

    private static final ThreadLocal<PortalCommitteePrincipal> CONTEXT = new TransmittableThreadLocal<>();

    private PortalCommitteeContext() {
    }

    public static void set(PortalCommitteePrincipal principal) {
        CONTEXT.set(principal);
    }

    public static PortalCommitteePrincipal get() {
        return CONTEXT.get();
    }

    public static Long getConferenceId() {
        return Optional.ofNullable(get()).map(PortalCommitteePrincipal::getConfId).orElse(null);
    }

    public static String getEmail() {
        return Optional.ofNullable(get()).map(PortalCommitteePrincipal::getEmail).orElse(null);
    }

    public static String getRole() {
        return Optional.ofNullable(get()).map(PortalCommitteePrincipal::getRole).orElse(null);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

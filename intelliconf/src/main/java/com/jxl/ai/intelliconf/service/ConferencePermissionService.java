package com.jxl.ai.intelliconf.service;

import com.jxl.ai.intelliconf.enums.ConferenceRole;

public interface ConferencePermissionService {

    void requireAtLeastRole(Long confId, ConferenceRole requiredRole);
}

package com.jxl.ai.intelliconf.mail.provider;

import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class MailProviderFactory {

    private final Map<MailProviderType, MailProvider> providerMap = new EnumMap<>(MailProviderType.class);

    public MailProviderFactory(List<MailProvider> providers) {
        for (MailProvider provider : providers) {
            providerMap.put(provider.type(), provider);
        }
    }

    public MailProvider getProvider(MailProviderType type) {
        MailProvider provider = providerMap.get(type);
        if (provider == null) {
            throw new ClientException("不支持的邮件服务商类型: " + type);
        }
        return provider;
    }
}

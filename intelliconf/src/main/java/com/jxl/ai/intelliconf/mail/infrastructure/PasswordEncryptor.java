package com.jxl.ai.intelliconf.mail.infrastructure;

import com.jxl.ai.intelliconf.toolkit.EncryptionUtil;
import org.springframework.stereotype.Component;

@Component
public class PasswordEncryptor {

    public String encrypt(String rawPassword) {
        return EncryptionUtil.encryptEmail(rawPassword);
    }

    public String decrypt(String passwordCipher) {
        return EncryptionUtil.decryptEmail(passwordCipher);
    }
}

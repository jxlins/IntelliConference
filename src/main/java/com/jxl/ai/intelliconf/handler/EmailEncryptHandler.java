package com.jxl.ai.intelliconf.handler;

import com.jxl.ai.intelliconf.toolkit.EncryptionUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 邮箱加密处理器
 */
public class EmailEncryptHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        // 入库前：加密
        ps.setString(i, EncryptionUtil.encryptEmail(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 出库后：解密
        return EncryptionUtil.decryptEmail(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return EncryptionUtil.decryptEmail(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return EncryptionUtil.decryptEmail(cs.getString(columnIndex));
    }
}
package com.jxl.ai.intelliconf.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * mybatis-plus 原数据自动填充类
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createTime", Date.class, new Date());
        strictInsertFill(metaObject, "updateTime", Date.class, new Date());
        strictInsertFill(metaObject, "delFlag", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
    }
}

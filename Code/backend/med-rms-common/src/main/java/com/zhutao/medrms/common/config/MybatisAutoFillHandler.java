package com.zhutao.medrms.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * v1.46 BUG #101 修复：全局 MyBatis-Plus 字段自动填充。
 * 扫描实体类的 @TableField(fill=FieldFill.INSERT) 字段，INSERT 时自动填 createdAt；
 * @TableField(fill=FieldFill.INSERT_UPDATE) 字段在 INSERT 和 UPDATE 时自动填 updatedAt。
 *
 * 背景：之前项目无全局 handler，service 显式 set 才能填充；未 set 的字段全为 NULL。
 * 前端典型表现：通知列表"创建时间"显示 "-"、变更列表"申请时间"显示空白。
 */
@Slf4j
@Component
public class MybatisAutoFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "created_at", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updated_at", LocalDateTime.class, now);
        log.debug("MyBatis-Plus 自动填充 INSERT: createdAt/updatedAt = {}", now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictUpdateFill(metaObject, "updated_at", LocalDateTime.class, now);
        log.debug("MyBatis-Plus 自动填充 UPDATE: updatedAt = {}", now);
    }
}

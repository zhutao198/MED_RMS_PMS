package com.zhutao.medrms.esignature.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.esignature.domain.entity.SignatureIntent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface SignatureIntentMapper extends BaseMapper<SignatureIntent> {

    /**
     * v1.47 BUG #139 P0 修复：自定义 INSERT 语句，避免 MyBatis-Plus FieldStrategy 漏列
     * 原 BaseMapper.insert 自动生成的 SQL 漏掉 requester_id/intent_code（NOT NULL 违反）
     */
    @Insert("""
        INSERT INTO esign_schema.t_signature_intent
          (intent_no, requester_id, document_type, document_id,
           intent_code, meaning_code, status, expires_at, created_at)
        VALUES
          (#{intentNo}, #{requesterId}, #{documentType}, #{documentId},
           #{intentCode}, #{meaningCode}, #{status}, #{expiresAt}, #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertIntent(SignatureIntent intent);
}

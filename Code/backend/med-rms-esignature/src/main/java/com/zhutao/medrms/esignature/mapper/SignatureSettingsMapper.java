package com.zhutao.medrms.esignature.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.esignature.domain.entity.SignatureSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SignatureSettingsMapper extends BaseMapper<SignatureSettings> {

    @Select("SELECT * FROM esign_schema.t_signature_settings WHERE user_id = #{userId} AND is_deleted = false")
    SignatureSettings selectByUserId(@Param("userId") Long userId);
}
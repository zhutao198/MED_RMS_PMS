package com.zhutao.medrms.esignature.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.esignature.domain.entity.ElectronicSignature;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ElectronicSignatureMapper extends BaseMapper<ElectronicSignature> {

    @Select("SELECT * FROM esign_schema.t_signature_record WHERE document_type = #{documentType} AND document_id = #{documentId} ORDER BY signed_at DESC")
    List<ElectronicSignature> selectByEntity(@Param("documentType") String documentType, @Param("documentId") Long documentId);

    @Select("SELECT * FROM esign_schema.t_signature_record WHERE id = #{id}")
    ElectronicSignature selectById(@Param("id") Long id);

    @Select("SELECT * FROM esign_schema.t_signature_record WHERE signer_id = #{signerId} ORDER BY signed_at DESC")
    List<ElectronicSignature> selectBySigner(@Param("signerId") Long signerId);
}
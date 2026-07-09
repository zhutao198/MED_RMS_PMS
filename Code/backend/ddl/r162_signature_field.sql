-- R162: 扩展电子签名字段以容纳 RSA-SHA256 签名值（原 SHA-256 64 字符→ RSA 2048 Base64 ~344 字符）
ALTER TABLE esign_schema.t_signature_record ALTER COLUMN signature_value TYPE VARCHAR(512);
ALTER TABLE esign_schema.t_signature_record ALTER COLUMN signature_hash TYPE VARCHAR(512);
ALTER TABLE esign_schema.t_signature_record ALTER COLUMN entity_hash TYPE VARCHAR(512);

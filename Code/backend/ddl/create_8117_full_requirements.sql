-- Create complete 8117 project requirements based on PRD
-- Project ID is 8

-- Create URS requirements (User Requirements)
INSERT INTO req_schema.t_requirement (requirement_no, title, description, requirement_type, project_id, status, priority, risk_level, safety_class, is_deleted, created_at, updated_at)
SELECT * FROM (
    SELECT 'URS-8117-001' as requirement_no, '12导联心电采集' as title, '系统应在30秒内完成12导联同步心电图采集，支持10秒/30秒/60秒/5分钟采集时长' as description, 'URS' as requirement_type, 8 as project_id, 'Approved' as status, 'MUST' as priority, 'HIGH' as risk_level, 'B' as safety_class, false as is_deleted, NOW() as created_at, NOW() as updated_at
    UNION ALL SELECT 'URS-8117-002', 'AI辅助诊断', '系统应内置智能心电分析算法，支持39+项检测能力，阳性检出率>=95%' as description, 'URS', 8, 'Approved', 'MUST', 'HIGH', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'URS-8117-003', '4G+WiFi双模联网', '系统应支持4G+WiFi双模，自动上传至平台成功率>=99.5%' as description, 'URS', 8, 'Approved', 'MUST', 'MEDIUM', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'URS-8117-004', '危急值预警', '系统应在3秒内预警危急值，支持危急/预警/普通三级预警' as description, 'URS', 8, 'Approved', 'MUST', 'HIGH', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'URS-8117-005', '平台深度融合', '系统应与中旗心电网络平台深度集成，支持Worklist和权限分级管理' as description, 'URS', 8, 'Approved', 'MUST', 'MEDIUM', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'URS-8117-006', '手持轻便设计', '系统重量<=500g，>=5寸高清彩屏，适合单手操作' as description, 'URS', 8, 'Approved', 'MUST', 'MEDIUM', 'C', false, NOW(), NOW()
    UNION ALL SELECT 'URS-8117-007', '电池续航', '系统连续工作时间>=8小时，待机时间>=72小时，充电时间<=3小时' as description, 'URS', 8, 'Approved', 'MUST', 'MEDIUM', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'URS-8117-008', '导联接反处理', '导联接反后有提示音及示意图，允许修正导联无需二次采集' as description, 'URS', 8, 'Approved', 'MUST', 'MEDIUM', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'URS-8117-009', '自动最优波形选取', '系统应自动选取最好10秒波形进行打印，避免重新采集' as description, 'URS', 8, 'Approved', 'MUST', 'LOW', 'C', false, NOW(), NOW()
    UNION ALL SELECT 'URS-8117-010', '锁屏功能', '系统应支持滑动解锁/密码解锁，防误触' as description, 'URS', 8, 'Approved', 'MUST', 'LOW', 'C', false, NOW(), NOW()
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM req_schema.t_requirement WHERE requirement_no = 'URS-8117-001');

-- Create PRS requirements (Product Requirements)
INSERT INTO req_schema.t_requirement (requirement_no, title, description, requirement_type, project_id, status, priority, risk_level, safety_class, is_deleted, created_at, updated_at)
SELECT * FROM (
    SELECT 'PRS-8117-001' as requirement_no, 'PRS-12导联心电采集' as title, '产品应支持32kHz采样率，24位分辨率，符合JJG 543-2026标准' as description, 'PRS' as requirement_type, 8 as project_id, 'Approved' as status, 'MUST' as priority, 'HIGH' as risk_level, 'B' as safety_class, false as is_deleted, NOW() as created_at, NOW() as updated_at
    UNION ALL SELECT 'PRS-8117-002', 'PRS-AI辅助诊断', '产品应采用自研心电算法，支持NMPA三类注册' as description, 'PRS', 8, 'Approved', 'MUST', 'HIGH', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'PRS-8117-003', 'PRS-4G+WiFi双模', '产品应内置4G模块，支持2.4G/5G双频WiFi' as description, 'PRS', 8, 'Approved', 'MUST', 'MEDIUM', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'PRS-8117-004', 'PRS-危急值预警', '产品应在检测到危急值时3秒内发出预警' as description, 'PRS', 8, 'Approved', 'MUST', 'HIGH', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'PRS-8117-005', 'PRS-平台融合', '产品应支持HL7 aECG/XML格式数据交互' as description, 'PRS', 8, 'Approved', 'MUST', 'MEDIUM', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'PRS-8117-006', 'PRS-轻便设计', '产品应采用人体工学设计，重量<=500g' as description, 'PRS', 8, 'Approved', 'MUST', 'MEDIUM', 'C', false, NOW(), NOW()
    UNION ALL SELECT 'PRS-8117-007', 'PRS-电池管理', '产品应采用5000mAh锂电池，支持Type-C快充' as description, 'PRS', 8, 'Approved', 'MUST', 'MEDIUM', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'PRS-8117-008', 'PRS-导联检测', '产品应自动检测导联脱落，脱落时声光报警' as description, 'PRS', 8, 'Approved', 'MUST', 'MEDIUM', 'B', false, NOW(), NOW()
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM req_schema.t_requirement WHERE requirement_no = 'PRS-8117-001');

-- Create SRS requirements (System Requirements)
INSERT INTO req_schema.t_requirement (requirement_no, title, description, requirement_type, project_id, status, priority, risk_level, safety_class, is_deleted, created_at, updated_at)
SELECT * FROM (
    SELECT 'SRS-8117-001' as requirement_no, 'SRS-心电采集模块' as title, '心电采集模块应支持标准12导联同步采集，采样率32kHz，24位ADC' as description, 'SRS' as requirement_type, 8 as project_id, 'Approved' as status, 'MUST' as priority, 'HIGH' as risk_level, 'B' as safety_class, false as is_deleted, NOW() as created_at, NOW() as updated_at
    UNION ALL SELECT 'SRS-8117-002', 'SRS-AI分析模块', 'AI分析模块应实现39+项心电异常检测' as description, 'SRS', 8, 'Approved', 'MUST', 'HIGH', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'SRS-8117-003', 'SRS-通信模块', '通信模块应支持4G和双频WiFi自动切换' as description, 'SRS', 8, 'Approved', 'MUST', 'MEDIUM', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'SRS-8117-004', 'SRS-报警模块', '报警模块应在检测到危急值时3秒内通过屏幕和声音报警' as description, 'SRS', 8, 'Approved', 'MUST', 'HIGH', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'SRS-8117-005', 'SRS-数据接口', '数据接口应支持HL7 aECG/XML格式和RESTful API' as description, 'SRS', 8, 'Approved', 'MUST', 'MEDIUM', 'B', false, NOW(), NOW()
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM req_schema.t_requirement WHERE requirement_no = 'SRS-8117-001');

-- Create DRS requirements (Design Requirements)
INSERT INTO req_schema.t_requirement (requirement_no, title, description, requirement_type, project_id, status, priority, risk_level, safety_class, is_deleted, created_at, updated_at)
SELECT * FROM (
    SELECT 'DRS-8117-001' as requirement_no, 'DRS-ADC前端设计' as title, 'ADC前端采用24位采样，32kHz采样率，输入阻抗>=100MOhm，CMRR>=140dB' as description, 'DRS' as requirement_type, 8 as project_id, 'Approved' as status, 'MUST' as priority, 'HIGH' as risk_level, 'B' as safety_class, false as is_deleted, NOW() as created_at, NOW() as updated_at
    UNION ALL SELECT 'DRS-8117-002', 'DRS-AI算法实现', 'AI算法采用卷积神经网络，模型大小<=10MB，推理时间<=100ms' as description, 'DRS', 8, 'Approved', 'MUST', 'HIGH', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'DRS-8117-003', 'DRS-4G模块选型', '4G模块支持全网通，峰值速率100Mbps' as description, 'DRS', 8, 'Approved', 'MUST', 'MEDIUM', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'DRS-8117-004', 'DRS-报警电路', '报警电路采用蜂鸣器+LED组合，音量可调节' as description, 'DRS', 8, 'Approved', 'MUST', 'HIGH', 'B', false, NOW(), NOW()
    UNION ALL SELECT 'DRS-8117-005', 'DRS-电池管理IC', '电池管理IC支持过充过放保护，温控保护' as description, 'DRS', 8, 'Approved', 'MUST', 'MEDIUM', 'B', false, NOW(), NOW()
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM req_schema.t_requirement WHERE requirement_no = 'DRS-8117-001');
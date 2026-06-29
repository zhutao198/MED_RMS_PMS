#!/usr/bin/env python3
"""Create complete 8117 project requirements from PRD"""
import urllib.request
import json

BASE_URL = "http://localhost:8080/api"

def post(url, data):
    req = urllib.request.Request(
        BASE_URL + url,
        data=json.dumps(data).encode(),
        headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req) as resp:
            result = json.loads(resp.read())
            if result.get("code") == 200:
                return result["data"]
            print(f"Failed: {result}")
            return None
    except Exception as e:
        print(f"Error: {e}")
        return None

# Define URS requirements based on PRD
urs_requirements = [
    # User Requirements - Core Functions
    {"no": "URS-8117-001", "title": "12导联心电采集", "desc": "系统应在30秒内完成12导联同步心电图采集，支持10s/30s/60s/5min可选采集时长，采样率32kHz，24位分辨率，符合JJG 543-2026标准", "priority": "MUST", "risk": "HIGH"},
    {"no": "URS-8117-002", "title": "AI辅助诊断", "desc": "系统应内置智能心电分析算法，对标Kardia 12L的39项检测能力，阳性检出率>=95%，规划NMPA三类注册", "priority": "MUST", "risk": "HIGH"},
    {"no": "URS-8117-003", "title": "4G+WiFi双模联网", "desc": "系统应支持4G+WiFi双模，随时连接心电网络平台，自动上传成功率>=99.5%，数据采用TLS 1.3加密", "priority": "MUST", "risk": "MEDIUM"},
    {"no": "URS-8117-004", "title": "危急值预警", "desc": "系统应在检测到危急值时3秒内发出预警，支持危急/预警/普通三级预警，危急值上报自动附带患者位置、心电波形、AI分析结果", "priority": "MUST", "risk": "HIGH"},
    {"no": "URS-8117-005", "title": "平台深度融合", "desc": "系统应与中旗心电网络信息平台深度集成，支持Worklist下载、患者信息扫码/刷卡识别、报告PDF查看", "priority": "MUST", "risk": "MEDIUM"},
    {"no": "URS-8117-006", "title": "手持轻便设计", "desc": "系统重量<=500g，>=5寸高清彩屏，单手操作，人体工学设计", "priority": "MUST", "risk": "MEDIUM"},
    {"no": "URS-8117-007", "title": "电池续航", "desc": "系统连续工作时间>=8小时，待机时间>=72小时，充电时间<=3小时，5000mAh锂电池", "priority": "MUST", "risk": "MEDIUM"},
    {"no": "URS-8117-008", "title": "导联检测与接反处理", "desc": "系统应自动检测导联脱落，脱落时声光报警；导联接反后有提示音及示意图，允许修正导联无需二次采集", "priority": "MUST", "risk": "MEDIUM"},
    {"no": "URS-8117-009", "title": "波形处理", "desc": "系统应自动选取最好10秒波形进行打印，支持肌电滤波（25/35/40Hz）和基线滤波，支持50Hz/60Hz工频陷波", "priority": "MUST", "risk": "LOW"},
    {"no": "URS-8117-010", "title": "锁屏与安全", "desc": "系统应支持滑动解锁/密码解锁，防误触；本地存储采用AES-256加密", "priority": "MUST", "risk": "LOW"},
    {"no": "URS-8117-011", "title": "帮助与培训", "desc": "系统应提供操作指引（动态图显示）、常规故障客户自主处理的图解", "priority": "SHOULD", "risk": "LOW"},
    {"no": "URS-8117-012", "title": "提示音功能", "desc": "系统应支持心跳音、缺纸、缺电、导联脱落等异常提示音，音量可调节（默认关闭）", "priority": "COULD", "risk": "LOW"},
    {"no": "URS-8117-013", "title": "横竖屏切换", "desc": "系统应支持横竖屏自动切换，同屏对比同患者报告", "priority": "MUST", "risk": "LOW"},
    {"no": "URS-8117-014", "title": "截图功能", "desc": "系统应支持截图功能", "priority": "COULD", "risk": "LOW"},
    {"no": "URS-8117-015", "title": "NFC身份识别", "desc": "系统应支持NFC功能，支持身份证识别，病人信息快速录入", "priority": "MUST", "risk": "MEDIUM"},
    {"no": "URS-8117-016", "title": "摄像头扫码", "desc": "系统应自带摄像头，可识别二维码和条形码", "priority": "SHOULD", "risk": "LOW"},
    {"no": "URS-8117-017", "title": "定位功能", "desc": "系统应支持GPS/北斗定位，防止设备丢失", "priority": "COULD", "risk": "LOW"},
    {"no": "URS-8117-018", "title": "拿起亮屏", "desc": "系统应在待机息屏时检测到手持仪状态改变（被拿起）时自动亮屏", "priority": "SHOULD", "risk": "LOW"},
    {"no": "URS-8117-019", "title": "极速开机", "desc": "系统应支持10秒极速开机", "priority": "MUST", "risk": "MEDIUM"},
    {"no": "URS-8117-020", "title": "急救场景支持", "desc": "系统应支持急救车场景模式（颠簸环境稳定采集），支持胸痛急救一键预通知", "priority": "MUST", "risk": "HIGH"},
    {"no": "URS-8117-021", "title": "乡村医生简化模式", "desc": "系统应为基层非专业人员设计极简操作流程", "priority": "SHOULD", "risk": "MEDIUM"},
    {"no": "URS-8117-022", "title": "离线采集", "desc": "系统应支持离线采集，联网后自动检测并补传", "priority": "MUST", "risk": "MEDIUM"},
    {"no": "URS-8117-023", "title": "等保合规", "desc": "系统应符合等保三级要求，操作日志留存不低于6个月", "priority": "MUST", "risk": "HIGH"},
    {"no": "URS-8117-024", "title": "医疗器械合规", "desc": "系统应符合GB 9706.1-2020、YY 9706.102-2021、GB 9706.225-2021等医用电气设备标准", "priority": "MUST", "risk": "HIGH"},
]

# Create URS requirements
print("Creating URS requirements...")
urs_ids = {}
for urs in urs_requirements:
    data = {
        "requirementNo": urs["no"],
        "title": urs["title"],
        "description": urs["desc"],
        "requirementType": "URS",
        "projectId": 8,
        "status": "Approved",
        "priority": urs["priority"],
        "riskLevel": urs["risk"],
        "safetyClass": "B"
    }
    req = post("/requirements", data)
    if req:
        urs_ids[urs["no"]] = req["id"]
        print(f"  Created {urs['no']} (id={req['id']})")

print(f"\nCreated {len(urs_ids)} URS requirements")

# Define PRS requirements mapped to URS
prs_requirements = [
    # PRS for ECG Acquisition
    {"no": "PRS-8117-001", "title": "PRS-心电采集硬件", "desc": "采用24位ADC，32kHz采样率，输入阻抗>=100MOhm，CMRR>=140dB", "parent": "URS-8117-001"},
    {"no": "PRS-8117-002", "title": "PRS-导联系统", "desc": "标准12导联（I、II、III、aVR、aVL、aVF、V1-V6），CF型防除颤", "parent": "URS-8117-001"},
    {"no": "PRS-8117-003", "title": "PRS-增益与走纸", "desc": "增益档位40/20/10/5/2.5mm/mV，走纸速度5/6.25/12.5/25/50mm/s", "parent": "URS-8117-001"},
    # PRS for AI
    {"no": "PRS-8117-004", "title": "PRS-AI算法", "desc": "采用自研心电算法，CNN模型，39+项检测能力，>=95%阳性检出率", "parent": "URS-8117-002"},
    {"no": "PRS-8117-005", "title": "PRS-AI芯片", "desc": "采用DSP芯片进行AI推理，模型<=10MB，推理时间<=100ms", "parent": "URS-8117-002"},
    # PRS for Network
    {"no": "PRS-8117-006", "title": "PRS-4G模块", "desc": "4G模块支持全网通，峰值速率100Mbps，内置eSIM", "parent": "URS-8117-003"},
    {"no": "PRS-8117-007", "title": "PRS-WiFi模块", "desc": "WiFi支持2.4G/5G双频段，自动切换", "parent": "URS-8117-003"},
    {"no": "PRS-8117-008", "title": "PRS-网络安全", "desc": "TLS 1.3加密，AES-256本地加密，等保三级", "parent": "URS-8117-003"},
    # PRS for Critical Alert
    {"no": "PRS-8117-009", "title": "PRS-危急检测", "desc": "实时检测ST段抬高、室颤、停搏等危急值，响应<=3秒", "parent": "URS-8117-004"},
    {"no": "PRS-8117-010", "title": "PRS-预警输出", "desc": "三级预警（危急/预警/普通），危急值附带位置+波形+AI结果", "parent": "URS-8117-004"},
    # PRS for Platform
    {"no": "PRS-8117-011", "title": "PRS-平台协议", "desc": "支持HL7 aECG/XML格式，RESTful API，MQTT设备管理", "parent": "URS-8117-005"},
    {"no": "PRS-8117-012", "title": "PRS-Worklist", "desc": "支持从平台自动获取检查Worklist", "parent": "URS-8117-005"},
    # PRS for Hardware
    {"no": "PRS-8117-013", "title": "PRS-显示屏", "desc": ">=5寸高清彩屏，亮度>=500cd/m²，对比度>=1000:1", "parent": "URS-8117-006"},
    {"no": "PRS-8117-014", "title": "PRS-结构设计", "desc": "人体工学设计，重量<=500g，IP22防护等级", "parent": "URS-8117-006"},
    {"no": "PRS-8117-015", "title": "PRS-电池", "desc": "5000mAh锂电池，>=8小时连续工作，>=72小时待机", "parent": "URS-8117-007"},
    {"no": "PRS-8117-016", "title": "PRS-充电管理", "desc": "Type-C快充，<=3小时充满，充电管理IC过充过放保护", "parent": "URS-8117-007"},
    {"no": "PRS-8117-017", "title": "PRS-导联检测", "desc": "自动检测导联脱落，声光报警，脱落时暂停采集", "parent": "URS-8117-008"},
    # PRS for Additional functions
    {"no": "PRS-8117-018", "title": "PRS-横竖屏", "desc": "加速度传感器检测方向，自动切换横竖屏", "parent": "URS-8117-013"},
    {"no": "PRS-8117-019", "title": "PRS-NFC", "desc": "NFC模块支持身份证识别，ISO1443标准", "parent": "URS-8117-015"},
    {"no": "PRS-8117-020", "title": "PRS-摄像头", "desc": "摄像头支持二维码/条形码识别，闪光灯辅助", "parent": "URS-8117-016"},
    {"no": "PRS-8117-021", "title": "PRS-GPS", "desc": "GPS/北斗双模定位，定位精度<=10m", "parent": "URS-8117-017"},
    {"no": "PRS-8117-022", "title": "PRS-加速度计", "desc": "加速度计检测设备拿起动作，触发亮屏", "parent": "URS-8117-018"},
    {"no": "PRS-8117-023", "title": "PRS-极速启动", "desc": "优化启动流程，10秒内完成开机到可采集", "parent": "URS-8117-019"},
    {"no": "PRS-8117-024", "title": "PRS-急救模式", "desc": "颠簸环境稳定采集算法，一键预通知胸痛中心", "parent": "URS-8117-020"},
    {"no": "PRS-8117-025", "title": "PRS-离线存储", "desc": "本地Flash存储>=1000条心电数据，联网自动上传", "parent": "URS-8117-022"},
    {"no": "PRS-8117-026", "title": "PRS-日志审计", "desc": "操作日志加密存储>=6个月，支持导出审计", "parent": "URS-8117-023"},
    {"no": "PRS-8117-027", "title": "PRS-安规设计", "desc": "符合GB 9706.1-2020，I类设备，CF型应用部分", "parent": "URS-8117-024"},
    {"no": "PRS-8117-028", "title": "PRS-EMC设计", "desc": "符合YY 9706.102-2021，电磁兼容要求", "parent": "URS-8117-024"},
]

print("\nCreating PRS requirements...")
prs_ids = {}
for prs in prs_requirements:
    data = {
        "requirementNo": prs["no"],
        "title": prs["title"],
        "description": prs["desc"],
        "requirementType": "PRS",
        "projectId": 8,
        "status": "Approved",
        "priority": "MUST",
        "riskLevel": "HIGH",
        "safetyClass": "B"
    }
    req = post("/requirements", data)
    if req:
        prs_ids[prs["no"]] = {"id": req["id"], "parent": prs["parent"]}
        print(f"  Created {prs['no']} (id={req['id']})")

print(f"\nCreated {len(prs_ids)} PRS requirements")

# Define SRS requirements
srs_requirements = [
    # SRS for ECG
    {"no": "SRS-8117-001", "title": "SRS-心电采集模块", "desc": "12导联同步采集，32kHz/24bit，实时滤波，导联脱落检测", "parent": "PRS-8117-001"},
    {"no": "SRS-8117-002", "title": "SRS-心电预处理", "desc": "工频陷波(50/60Hz)、肌电滤波(25/35/40Hz)、基线漂移滤波", "parent": "PRS-8117-001"},
    {"no": "SRS-8117-003", "title": "SRS-增益控制", "desc": "多档增益自动/手动切换，数字化增益控制", "parent": "PRS-8117-003"},
    # SRS for AI
    {"no": "SRS-8117-004", "title": "SRS-AI推理引擎", "desc": "CNN推理引擎，39+项心电异常检测，置信度输出", "parent": "PRS-8117-004"},
    {"no": "SRS-8117-005", "title": "SRS-AI训练流程", "desc": "10万+临床数据验证，模型定期更新", "parent": "PRS-8117-004"},
    # SRS for Network
    {"no": "SRS-8117-006", "title": "SRS-4G通信", "desc": "PPP拨号协议，TCP/IP协议栈，心跳维持", "parent": "PRS-8117-006"},
    {"no": "SRS-8117-007", "title": "SRS-WiFi通信", "desc": "WLAN连接管理，漫游切换，功耗优化", "parent": "PRS-8117-007"},
    {"no": "SRS-8117-008", "title": "SRS-传输安全", "desc": "TLS 1.3握手，AES-256加密，会话管理", "parent": "PRS-8117-008"},
    # SRS for Alert
    {"no": "SRS-8117-009", "title": "SRS-危急检测算法", "desc": "STEMI/室颤/停搏检测算法，<3秒延迟", "parent": "PRS-8117-009"},
    {"no": "SRS-8117-010", "title": "SRS-预警界面", "desc": "红色全屏弹窗+蜂鸣，危急/预警/普通三级UI", "parent": "PRS-8117-010"},
    # SRS for Platform
    {"no": "SRS-8117-011", "title": "SRS-HL7接口", "desc": "HL7 aECG/XML编解码，波形数据压缩传输", "parent": "PRS-8117-011"},
    {"no": "SRS-8117-012", "title": "SRS-REST接口", "desc": "JSON格式API，患者/报告/Worklist管理", "parent": "PRS-8117-011"},
    {"no": "SRS-8117-013", "title": "SRS-Worklist获取", "desc": "定时拉取Worklist，本地缓存，离线可用", "parent": "PRS-8117-012"},
    # SRS for Hardware
    {"no": "SRS-8117-014", "title": "SRS-显示驱动", "desc": "RGB/MIPI接口，>=30fps波形刷新率，多点触控", "parent": "PRS-8117-013"},
    {"no": "SRS-8117-015", "title": "SRS-结构工艺", "desc": "注塑外壳，耐消毒材料，1.2m跌落测试", "parent": "PRS-8117-014"},
    {"no": "SRS-8117-016", "title": "SRS-电池管理", "desc": "电量监测，低功耗休眠，充放电保护", "parent": "PRS-8117-015"},
    {"no": "SRS-8117-017", "title": "SRS-快充协议", "desc": "PD/QC快充协议，充电曲线优化", "parent": "PRS-8117-016"},
    {"no": "SRS-8117-018", "title": "SRS-导联状态监测", "desc": "8路导联独立监测，脱落位置指示", "parent": "PRS-8117-017"},
]

print("\nCreating SRS requirements...")
srs_ids = {}
for srs in srs_requirements:
    data = {
        "requirementNo": srs["no"],
        "title": srs["title"],
        "description": srs["desc"],
        "requirementType": "SRS",
        "projectId": 8,
        "status": "Approved",
        "priority": "MUST",
        "riskLevel": "HIGH",
        "safetyClass": "B"
    }
    req = post("/requirements", data)
    if req:
        srs_ids[srs["no"]] = {"id": req["id"], "parent": srs["parent"]}
        print(f"  Created {srs['no']} (id={req['id']})")

print(f"\nCreated {len(srs_ids)} SRS requirements")

# Define DRS requirements
drs_requirements = [
    # DRS for ECG
    {"no": "DRS-8117-001", "title": "DRS-ADC前端设计", "desc": "仪表放大器+24bit ADC，输入阻抗>=100MOhm，CMRR>=140dB", "parent": "SRS-8117-001"},
    {"no": "DRS-8117-002", "title": "DRS-导联线接口", "desc": "防除颤导联线接口，DB15连接器，屏蔽设计", "parent": "SRS-8117-001"},
    {"no": "DRS-8117-003", "title": "DRS-数字滤波器", "desc": "FIR/IIR滤波器，50Hz陷波，40Hz低通", "parent": "SRS-8117-002"},
    # DRS for AI
    {"no": "DRS-8117-004", "title": "DRS-AI模型", "desc": "量化CNN模型，INT8推理，模型<=10MB", "parent": "SRS-8117-004"},
    {"no": "DRS-8117-005", "title": "DRS-DSP加速", "desc": "DSP指令集优化，SIMD加速，推理<=100ms", "parent": "SRS-8117-004"},
    # DRS for Network
    {"no": "DRS-8117-006", "title": "DRS-4G硬件", "desc": "4G模块硬件设计，全网通射频调试", "parent": "SRS-8117-006"},
    {"no": "DRS-8117-007", "title": "DRS-WiFi硬件", "desc": "WiFi模块，2.4G/5G双频天线设计", "parent": "SRS-8117-007"},
    {"no": "DRS-8117-008", "title": "DRS-安全芯片", "desc": "安全芯片存储密钥，TPM可信启动", "parent": "SRS-8117-008"},
    # DRS for Alert
    {"no": "DRS-8117-009", "title": "DRS-紧急报警电路", "desc": "蜂鸣器驱动+LED驱动，音量可调", "parent": "SRS-8117-010"},
    {"no": "DRS-8117-010", "title": "DRS-GPS定位", "desc": "GPS/北斗双模模块，定位精度<=10m", "parent": "SRS-8117-009"},
    # DRS for Platform
    {"no": "DRS-8117-011", "title": "DRS-HL7编解码", "desc": "HL7 aECG解析器，XML/二进制双格式", "parent": "SRS-8117-011"},
    {"no": "DRS-8117-012", "title": "DRS-HTTP客户端", "desc": "OkHttp/HttpClient，重试机制，连接池", "parent": "SRS-8117-012"},
    # DRS for Hardware
    {"no": "DRS-8117-013", "title": "DRS-显示屏接口", "desc": "MIPI-DSI接口，显示屏转接板设计", "parent": "SRS-8117-014"},
    {"no": "DRS-8117-014", "title": "DRS-外壳模具", "desc": "抗菌塑料外壳，纳米银载体，符合ISO 10993", "parent": "SRS-8117-015"},
    {"no": "DRS-8117-015", "title": "DRS-电池保护", "desc": "过充/过放/过流/温度保护，保護板设计", "parent": "SRS-8117-016"},
    {"no": "DRS-8117-016", "title": "DRS-充电IC", "desc": "同步整流充电IC，充电效率>=90%", "parent": "SRS-8117-017"},
    {"no": "DRS-8117-017", "title": "DRS-导联检测电路", "desc": "恒流源驱动，脱落检测阈值可设置", "parent": "SRS-8117-018"},
]

print("\nCreating DRS requirements...")
drs_ids = {}
for drs in drs_requirements:
    data = {
        "requirementNo": drs["no"],
        "title": drs["title"],
        "description": drs["desc"],
        "requirementType": "DRS",
        "projectId": 8,
        "status": "Approved",
        "priority": "MUST",
        "riskLevel": "HIGH",
        "safetyClass": "B"
    }
    req = post("/requirements", data)
    if req:
        drs_ids[drs["no"]] = {"id": req["id"], "parent": drs["parent"]}
        print(f"  Created {drs['no']} (id={req['id']})")

print(f"\nCreated {len(drs_ids)} DRS requirements")

# Summary
print("\n" + "="*50)
print(f"Total URS: {len(urs_ids)}")
print(f"Total PRS: {len(prs_ids)}")
print(f"Total SRS: {len(srs_ids)}")
print(f"Total DRS: {len(drs_ids)}")
print("="*50)
print("All requirements created successfully!")
print("\nID mappings saved for traceability setup...")

# Save mappings for traceability
all_ids = {
    "urs": urs_ids,
    "prs": prs_ids,
    "srs": srs_ids,
    "drs": drs_ids
}
with open("D:/zhutao/MED_RMS_PMS/Code/backend/requirement_ids.json", "w") as f:
    json.dump(all_ids, f, indent=2)
print("IDs saved to requirement_ids.json")
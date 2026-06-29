================================================================================
Med-RMS JaCoCo 覆盖率分析报告（W9-D1）
================================================================================

## 1. 总体指标（Service + Controller）
  - 模块数：10
  - 覆盖行：2392 / 7352
  - 总体行覆盖：32.5%
  - Service 类数：47
  - Controller 类数：32

## 2. Service 行覆盖盲区 Top 10（最低优先）
  模块                     类名                                          覆盖    未覆      率
  ---------------------- ---------------------------------------- ----- ----- ------
  med-rms-admin          JwtService                                   0    72   0.0%
  med-rms-admin          SystemService                                0    64   0.0%
  med-rms-compliance     PrCorrectionService                          0    39   0.0%
  med-rms-compliance     ReportConfigService                          0    51   0.0%
  med-rms-compliance     StatisticsService                            0   116   0.0%
  med-rms-compliance     SafetyClassificationService                  0    34   0.0%
  med-rms-compliance     RegulatoryMappingService                     0    15   0.0%
  med-rms-compliance     DashboardConfigService                       0   113   0.0%
  med-rms-change         ChangeAttachmentService                      0    48   0.0%
  med-rms-notification   EmailQueueService                            0    38   0.0%

## 3. Service 行覆盖优秀 Top 5（≥ 80%）
  med-rms-requirement    RequirementPoolService                      46     0 100.0%
  med-rms-compliance     ProblemReportService                        29     0 100.0%
  med-rms-compliance     ReportTemplateService                        8     0 100.0%
  med-rms-compliance     Iec62304ChecklistService                    87     2  97.8%
  med-rms-esignature     ElectronicSignatureService                 122     3  97.6%

## 4. 按模块聚合行覆盖
  med-rms-admin             137 / 377     36.3%
  med-rms-change            325 / 437     74.4%
  med-rms-compliance        552 / 1197    46.1%
  med-rms-esignature        181 / 232     78.0%
  med-rms-notification       50 / 116     43.1%
  med-rms-project           111 / 532     20.9%
  med-rms-requirement       486 / 605     80.3%
  med-rms-risk              119 / 232     51.3%
  med-rms-traceability      418 / 572     73.1%

## 5. 业务 Service 覆盖率统计（合并各模块）
  Service 覆盖：2379 / 4300 = 55.3%
  [WARN] 距离 80% 目标还差 24.7%

  Controller 覆盖：189 / 660 = 28.6%
  [WARN] 距离 85% 目标还差 56.4%

================================================================================

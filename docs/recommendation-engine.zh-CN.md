# 附近救助 / TNR「推荐引擎」说明

本仓库里没有单独的微服务叫「推荐引擎」，**推荐结果由后端距离计算 + 前端展示** 组成，数据来自机构目录表。

## 1. 照片评估后的「附近资源」

- **后端**：`AssessmentService.buildActions` 使用评估保存的 **纬度/经度**（无坐标时曾用默认参考点），调用  
  `RescueDirectoryService.nearestDtos` / `TnrDirectoryService.nearestDtos` 各取最近若干条，写入 `BranchActionsDto.rescueNearby` / `tnrNearby`。
- **前端**：`index.html` 的 `renderCenterList` 渲染列表；提交分析前若仅有地址会 **前向地理编码** 再带 `latitude`/`longitude`，以便与热力图、机构距离一致。

## 2. 站长 Agent（Python）里的附近机构

- **cat-agent**：`get_nearby_shelters_impl` 请求 Java 的  
  `GET /api/v1/tnr-directory/nearest`、`GET /api/v1/rescue-directory/nearest`（`CAT_RESCUE_API_BASE` 指向 Spring）。
- 与评估页列表共用同一套 **nearest** 逻辑，只是对话场景下由模型总结成自然语言。

## 3. 扩展建议

- 若要「更智能」的排序（例如优先有急诊、夜间营业），需要在 **Java 目录服务或新聚合接口** 增加业务规则，而不是只改前端文案。

## 4. 与 `cat-agent/recommendation_engine/` 的关系

目录 `cat-agent/recommendation_engine/` 是仓库内的 **历史/实验代码**，**当前线上站长对话**走的是 `cat-agent/api.py` + `ai_service.py`，不会 import 该子项目。若不再维护可整目录删除；与上文「推荐」数据流无耦合。

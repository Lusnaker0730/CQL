# 追溯矩陣 (Traceability Matrix)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-TM-1.0.0 |
| 版本 | 1.0.0 |
| 日期 | 2026-03-13 |
| 產品名稱 | CQL Platform — 臨床品質語言視覺化編輯與執行平台 |
| 審核者 | _________________ |
| 核准者 | _________________ |

## 修訂歷史

| 版本 | 日期 | 變更說明 | 作者 |
|------|------|---------|------|
| 1.0.0 | 2026-03-13 | 自動產生 | 系統 |

---

## 1. 目的

本文件建立需求、設計、驗證、風險之間的雙向追溯關係，確保每項需求均已被設計、驗證並進行風險分析。

## 2. 需求 → 設計 → 驗證 → 風險 追溯表

| 需求 | 設計 | 驗證 | 風險 | 狀態 |
|------|------|------|------|------|

| [#60](https://github.com/Lusnaker0730/CQL/issues/60) [需求] 品質指標儀表板應提供視覺化數據分析與閾值警示 |  | [#62](https://github.com/Lusnaker0730/CQL/issues/62)  |  | 缺少：設計、風險 |

| [#50](https://github.com/Lusnaker0730/CQL/issues/50) [需求] 系統應支援繁體中文與英文雙語介面 |  | [#55](https://github.com/Lusnaker0730/CQL/issues/55)  |  | 缺少：設計、風險 |

| [#45](https://github.com/Lusnaker0730/CQL/issues/45) [需求] 系統應具備完整的輸入驗證與注入攻擊防護 | [#51](https://github.com/Lusnaker0730/CQL/issues/51)  | [#61](https://github.com/Lusnaker0730/CQL/issues/61)  | [#58](https://github.com/Lusnaker0730/CQL/issues/58)  | 完整 |

| [#39](https://github.com/Lusnaker0730/CQL/issues/39) [需求] 系統應支援 EHR/HIS 系統連接與病患資料匯入 | [#44](https://github.com/Lusnaker0730/CQL/issues/44)  | [#54](https://github.com/Lusnaker0730/CQL/issues/54)  | [#48](https://github.com/Lusnaker0730/CQL/issues/48)  | 完整 |

| [#38](https://github.com/Lusnaker0730/CQL/issues/38) [需求] 測試案例建構器應提供視覺化 FHIR Bundle 編輯與族群驗證 | [#40](https://github.com/Lusnaker0730/CQL/issues/40)  | [#53](https://github.com/Lusnaker0730/CQL/issues/53)  | [#46](https://github.com/Lusnaker0730/CQL/issues/46)  | 完整 |

| [#37](https://github.com/Lusnaker0730/CQL/issues/37) [需求] eCQM 撰寫工具應提供視覺化品質量測定義與 CQL 自動產生 | [#42](https://github.com/Lusnaker0730/CQL/issues/42)  | [#57](https://github.com/Lusnaker0730/CQL/issues/57)  | [#49](https://github.com/Lusnaker0730/CQL/issues/49)  | 完整 |

| [#36](https://github.com/Lusnaker0730/CQL/issues/36) [需求] CQL Builder 應提供視覺化元件輔助使用者建構 CQL 語句 | [#41](https://github.com/Lusnaker0730/CQL/issues/41)  | [#56](https://github.com/Lusnaker0730/CQL/issues/56)  | [#47](https://github.com/Lusnaker0730/CQL/issues/47)  | 完整 |

| [#35](https://github.com/Lusnaker0730/CQL/issues/35) [需求] 系統應提供安全的認證授權機制防止未授權存取 | [#43](https://github.com/Lusnaker0730/CQL/issues/43)  | [#59](https://github.com/Lusnaker0730/CQL/issues/59)  | [#52](https://github.com/Lusnaker0730/CQL/issues/52)  | 完整 |

| [#25](https://github.com/Lusnaker0730/CQL/issues/25) [需求] CDS Hooks 應根據臨床決策規則產生建議卡片 | [#28](https://github.com/Lusnaker0730/CQL/issues/28)  | [#34](https://github.com/Lusnaker0730/CQL/issues/34)  | [#31](https://github.com/Lusnaker0730/CQL/issues/31)  | 完整 |

| [#24](https://github.com/Lusnaker0730/CQL/issues/24) [需求] 品質量測執行結果應正確計算病患族群 | [#27](https://github.com/Lusnaker0730/CQL/issues/27)  | [#33](https://github.com/Lusnaker0730/CQL/issues/33)  | [#30](https://github.com/Lusnaker0730/CQL/issues/30)  | 完整 |

| [#23](https://github.com/Lusnaker0730/CQL/issues/23) [需求] CQL 翻譯服務應在 3 秒內完成回應 | [#26](https://github.com/Lusnaker0730/CQL/issues/26)  | [#32](https://github.com/Lusnaker0730/CQL/issues/32)  | [#29](https://github.com/Lusnaker0730/CQL/issues/29)  | 完整 |


## 3. 覆蓋率統計

| 項目 | 數量 | 百分比 |
|------|------|--------|
| 總需求數 | 11 | 100% |
| 有設計對應 | 9 | 82% |
| 有驗證對應 | 11 | 100% |
| 有風險對應 | 9 | 82% |

## 4. 缺口分析


以下需求尚缺追溯對應：


- **#60 [需求] 品質指標儀表板應提供視覺化數據分析與閾值警示**：缺少 設計、風險

- **#50 [需求] 系統應支援繁體中文與英文雙語介面**：缺少 設計、風險



---

*本文件由 CQL Platform 法規文件產生器自動產生*

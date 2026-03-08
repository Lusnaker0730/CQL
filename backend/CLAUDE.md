# Backend — Spring Boot 3.2 / Java 21

## 架構：Controller → Service → Repository

### Controller 模式
```java
@RestController
@RequestMapping("/api/xxx")
@RequiredArgsConstructor
@Tag(name = "XXX")
public class XxxController {
    private final XxxService xxxService;

    @PostMapping
    @Operation(summary = "...")
    public ResponseEntity<XxxResponse> doSomething(@Valid @RequestBody XxxRequest request) {
        return ResponseEntity.ok(xxxService.doSomething(request));
    }
}
```

### Service 模式
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class XxxService {
    private final XxxRepository xxxRepository;

    @Transactional  // 多步驟變更必加
    public Xxx doSomething(XxxRequest request) {
        // 驗證 → 業務邏輯 → 回傳
        // 錯誤拋 domain exception，不要回傳 HTTP 概念
    }
}
```

### 禁止事項
- Service 層**禁止**使用 `HttpServletRequest`、`@ResponseStatus`、`@PathVariable`
- **禁止** `@Autowired` 在欄位上，用 `@RequiredArgsConstructor` + `final`
- **禁止**在 Service 層處理 HTTP 狀態碼

## 例外處理

`GlobalExceptionHandler` 統一映射例外到 HTTP 回應：

| Exception | HTTP Status |
|-----------|-------------|
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| `ValidationException` | 400 (附 details list) |
| `CqlTranslationException` | 400 (附 error list) |
| `CqlGenerationException` | 422 (附 details) |
| `CqlExecutionException` | 500 或 504 (timeout) |
| `FhirServerUnavailableException` | 503 |

新增例外：繼承 `RuntimeException`，加 `@Getter`，放在 `exception/` 套件。

## CQL 產生引擎

資料流：`前端 JSON → AuthoringController → CqlGenerationService → CqlArtifactBuilder → FreeMarker → CQL 字串`

關鍵類別：
- `CqlArtifactBuilder` — 組裝 context Map，呼叫 `artifact.ftl`
- `EcqmCqlBuilder` — eCQM 版本，呼叫 `ecqm-artifact.ftl`
- `ExpressionCqlEngine` — 修飾器 / 元素的 CQL 片段產生
- `CqlTemplateEngine` — FreeMarker 包裝器（載入 `classpath:templates/cql/`）

模板目錄：`src/main/resources/templates/cql/`
```
artifact.ftl, ecqm-artifact.ftl    — 主模板
modifiers/   (19 files)            — 修飾器模板
elements/    (3 files)             — AgeRange, Gender, GenericResource
fragments/   (2 files)             — cds-card, error-statement
parameters/  (1 file)              — defaults
ecqm/        (1 file)              — standard-sde
```

## 資料庫

- PostgreSQL (prod) / H2 (dev)
- Schema 由 Flyway 管理：`src/main/resources/db/migration/`（V1~V40）
- JPA `ddl-auto=validate`（不會自動建表）
- 新增表/欄位：建立 `V{N+1}__description.sql` 遷移檔

## 測試模式

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class XxxControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean XxxService xxxService;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void shouldDoSomething() throws Exception {
        when(xxxService.doSomething(any())).thenReturn(expected);
        mockMvc.perform(post("/api/xxx").contentType(APPLICATION_JSON).content(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.field").value("expected"));
    }
}
```

## 常用指令

```bash
mvn=/c/Users/alumi/apache-maven-3.9.12/bin/mvn
$mvn -f backend/pom.xml compile          # 編譯
$mvn -f backend/pom.xml test             # 全部測試
$mvn -f backend/pom.xml test -Dtest=XxxTest  # 單一測試
```

## 關鍵設定

- `application.yml` — 主配置（port 8080, HikariCP max=20）
- `application-dev.yml` — H2 記憶體資料庫
- `application-docker.yml` — Docker 環境變數覆蓋
- `logback-spring.xml` — 結構化日誌

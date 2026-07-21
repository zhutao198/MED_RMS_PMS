# 产品管理模块详细设计

> 版本：v1.1（应用 2026-07-21 评审，18 项修复）
> 日期：2026-07-21
> 状态：实施稿（评审通过）
> 关联节点：R199 v1.62

---

## 1. 概述

### 1.1 背景

需求池「新建需求」和 URS 创建页面中存在「适用产品」下拉框，当前为硬编码（心电监护仪 v3.0 / 脉搏血氧仪 v2.1），无法选择用户所需产品（如 8333）。同时系统中「项目」与「产品」概念混用——项目名直接当产品名，缺乏独立的产品字典管理。

### 1.2 目标

1. 建立独立的产品字典管理模块
2. 理清「产品」与「项目」「需求」的领域关系
3. 所有关联场景从硬编码改为动态加载
4. 提供完整的管理后台（CRUD + Excel 导出 + 双签约束）
5. 满足 21 CFR Part 11 §11.200 主数据修改双签要求

### 1.3 领域模型关系

```
产品（prd_schema.t_product）   ← 新增，独立限界上下文
  │
  ├── 项目（proj_schema.t_project）
  │   └── product_id（可空，一个产品可有多个项目）
  │
  ├── 需求（req_schema.t_requirement）
  │   └── product_id（可空，需求关联到具体产品型号）
  │
  └── 需求池（req_schema.t_requirement_pool）
      └── product_id（可空，池中条目先选产品后立项）
```

**核心原则：**
- **产品** = 被制造的医疗器械设备型号（如 8333、iMEC 15）
- **项目** = 某产品的某版本研发组织工作（如 8333 v3.0 项目）
- **需求** = 对产品的功能/性能/安全要求

### 1.4 设计原则
- **独立限界上下文**：med-rms-product 是 Spring Boot 独立 Maven 模块
- **跨模块解耦**：med-rms-project 不依赖 med-rms-product，通过 JdbcTemplate 跨 schema 查询
- **21 CFR Part 11 合规**：trg_prevent_hard_delete + record_hash + 双签
- **复用现有模式**：ProductSelector 复用 ProjectSelector 模式；TimedCache 复用 R198b

---

## 2. 数据库设计

### 2.1 新建 Schema

```sql
CREATE SCHEMA IF NOT EXISTS prd_schema;
COMMENT ON SCHEMA prd_schema IS '产品管理限界上下文';
```

### 2.2 t_product 表（含合规）

```sql
CREATE TABLE prd_schema.t_product (
    id              BIGSERIAL PRIMARY KEY,
    product_code    VARCHAR(50)  NOT NULL,   -- 产品型号编码（如 8333、iMEC15）
    product_name    VARCHAR(200) NOT NULL,   -- 产品名称（如 8333 多参数监护仪）
    product_line    VARCHAR(50),             -- 产品线（字典类型 product_line，R199 统一小写）
    status          VARCHAR(20) DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','DISCONTINUED','DEVELOPMENT')),
    description     TEXT,
    record_hash     VARCHAR(64),             -- R197 G17：防篡改校验和
    is_deleted      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Partial unique index：仅未删除时强制唯一，软删除后可重建
CREATE UNIQUE INDEX uq_product_code_active
    ON prd_schema.t_product(product_code) WHERE NOT is_deleted;

CREATE INDEX idx_product_line ON prd_schema.t_product(product_line) WHERE NOT is_deleted;
CREATE INDEX idx_product_status ON prd_schema.t_product(status) WHERE NOT is_deleted;

COMMENT ON TABLE  prd_schema.t_product IS '产品字典（医疗器械型号管理）';
COMMENT ON COLUMN prd_schema.t_product.product_code IS '产品型号编码，如 8333';
COMMENT ON COLUMN prd_schema.t_product.product_name IS '产品名称，如 8333 多参数监护仪';
COMMENT ON COLUMN prd_schema.t_product.product_line IS '产品线，关联 sys_schema.t_dict_item(dict_type=product_line)';
COMMENT ON COLUMN prd_schema.t_product.status IS 'ACTIVE=在产, DISCONTINUED=停产, DEVELOPMENT=开发中';
COMMENT ON COLUMN prd_schema.t_product.record_hash IS 'R197 G17：SHA-256 记录校验和';
```

### 2.3 字典数据（product_line — 统一小写）

```sql
-- R199 修复：原 v1.0 写 PRODUCT_LINE（大写），实际 DictItem API 返回小写，统一小写
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('product_line', 'MONITOR',      '监护仪',   1),
('product_line', 'ECG',          '心电',     2),
('product_line', 'SPO2',         '血氧',     3),
('product_line', 'NIBP',         '血压',     4),
('product_line', 'ULTRASOUND',   '超声',     5)
ON CONFLICT DO NOTHING;
```

### 2.4 合规触发器（R197 G16/G17）

```sql
-- G16：禁止硬删除（仅软删除走 is_deleted）
CREATE TRIGGER trg_prevent_hard_delete_product
BEFORE DELETE ON prd_schema.t_product
FOR EACH ROW EXECUTE FUNCTION compliance_schema.fn_prevent_hard_delete();

-- G17：record_hash 自动维护
CREATE TRIGGER trg_record_hash_product
BEFORE INSERT OR UPDATE ON prd_schema.t_product
FOR EACH ROW EXECUTE FUNCTION compliance_schema.fn_compute_record_hash();
```

### 2.5 现有表加 product_id 列（含数据迁移）

```sql
-- t_project
ALTER TABLE proj_schema.t_project
    ADD COLUMN product_id BIGINT REFERENCES prd_schema.t_product(id);
CREATE INDEX idx_proj_product ON proj_schema.t_project(product_id);

-- t_requirement
ALTER TABLE req_schema.t_requirement
    ADD COLUMN product_id BIGINT REFERENCES prd_schema.t_product(id);
CREATE INDEX idx_req_product ON req_schema.t_requirement(product_id);

-- t_requirement_pool
ALTER TABLE req_schema.t_requirement_pool
    ADD COLUMN product_id BIGINT REFERENCES prd_schema.t_product(id);
CREATE INDEX idx_pool_product ON req_schema.t_requirement_pool(product_id);

-- R199 数据迁移：基于现有硬编码反查回填 product_id
UPDATE proj_schema.t_project t
SET product_id = p.id
FROM prd_schema.t_product p
WHERE (t.name LIKE '%8333%'   AND p.product_code = '8333')
   OR (t.name LIKE '%iMEC 15%' AND p.product_code = 'iMEC15')
   OR (t.name LIKE '%心电监护仪 v3.0%' AND p.product_code = 'ECG-3')
   OR (t.name LIKE '%脉搏血氧仪 v2.1%' AND p.product_code = 'SPO2-2')
   OR (t.name LIKE '%无创血压%'       AND p.product_code = 'NIBP-3');

UPDATE req_schema.t_requirement r
SET product_id = p.id
FROM prd_schema.t_product p
WHERE r.product_code = p.product_code;  -- 假设现有有冗余 product_code 字段（实施时核实）

UPDATE req_schema.t_requirement_pool r
SET product_id = p.id
FROM prd_schema.t_product p
WHERE r.product_code = p.product_code;
```

### 2.6 Seed 数据

```sql
INSERT INTO prd_schema.t_product (product_code, product_name, product_line, status) VALUES
('8333',   '8333 多参数监护仪',  'MONITOR',    'ACTIVE'),
('iMEC15', 'iMEC 15 病人监护仪', 'MONITOR',    'ACTIVE'),
('ECG-3',  '心电监护仪 v3.0',    'ECG',        'ACTIVE'),
('SPO2-2', '脉搏血氧仪 v2.1',    'SPO2',       'ACTIVE'),
('NIBP-3', '无创血压监护模块',   'NIBP',       'ACTIVE');
```

### 2.7 DDL 文件

- 新建：`Code/backend/ddl/r199_product_mgmt.sql`（包含全部 2.1–2.6）

---

## 3. 后端设计

### 3.1 模块结构

新增 Maven 模块 `med-rms-product`，遵循现有 DDD 分层：

```
med-rms-product/
├── pom.xml
└── src/main/java/com/zhutao/medrms/product/
    ├── controller/
    │   └── ProductController.java
    ├── domain/
    │   └── entity/
    │       └── Product.java
    ├── mapper/
    │   └── ProductMapper.java
    ├── service/
    │   ├── ProductService.java
    │   └── ProductExportService.java     ← R199 v1.62：Excel 导出（POI）
    ├── dto/
    │   ├── ProductCreateRequest.java
    │   ├── ProductUpdateRequest.java     ← R199 v1.62：补 DTO
    │   ├── ProductResponse.java          ← R199 v1.62：补 DTO
    │   └── PageQuery.java                ← R199 v1.62：通用分页
    └── config/
        └── ProductCacheConfig.java       ← R199 v1.62：TimedCache 注入
```

### 3.2 Product 实体

```java
@Data
@TableName("prd_schema.t_product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String productCode;
    private String productName;
    private String productLine;   // 字典类型 product_line（R199 统一小写）
    private String status;        // ACTIVE / DISCONTINUED / DEVELOPMENT
    private String description;

    private String recordHash;    // R197 G17：校验和

    @TableLogic
    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### 3.3 ProductMapper

```java
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    List<Product> selectActiveByLine(@Param("productLine") String productLine);
}
```

### 3.4 ProductService（含 TimedCache 缓存 + 双签约束）

```java
@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final TimedCache<String, List<Product>> activeCache;  // R198b v1.61 TimedCache

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
        this.activeCache = TimedCache.<String, List<Product>>builder()
                .ttl(Duration.ofMinutes(5))
                .maxSize(100)
                .build();
    }

    public PageResult<Product> list(String keyword, String productLine, String status, int page, int size) {
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<Product>()
                .like(keyword != null, Product::getProductName, keyword)
                .or().like(keyword != null, Product::getProductCode, keyword)
                .eq(productLine != null, Product::getProductLine, productLine)
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getId);
        IPage<Product> p = productMapper.selectPage(new Page<>(page, size), q);
        return PageResult.of(p);
    }

    public List<Product> listAllActive() {
        // R199 v1.62：5min TTL 缓存，CRUD 时失效
        return activeCache.get("active", () ->
            productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, "ACTIVE")
                .orderByAsc(Product::getProductCode)));
    }

    public Product getById(Long id) {
        return productMapper.selectById(id);
    }

    /**
     * 创建产品 — R199 v1.62: 双签约束（21 CFR Part 11 §11.200）
     * 主数据修改需 2 个不同用户签名（同 admin 双签被拒 code=SY0101）
     */
    @Transactional
    @AuditLog(eventType="CREATE", entityType="PRODUCT", operation="创建产品",
              requireDoubleSign=true)  // 新增注解属性
    public Product create(ProductCreateRequest req, Long currentUserId, Long secondSignerId) {
        if (currentUserId.equals(secondSignerId)) {
            throw new BusinessException("SY0101", "主数据创建需双签，两人不可相同");
        }
        // ... 校验 + 写入
        activeCache.invalidate("active");
        return product;
    }

    @Transactional
    @AuditLog(eventType="MODIFY", entityType="PRODUCT", operation="编辑产品",
              requireDoubleSign=true)
    public Product update(Long id, ProductUpdateRequest req, Long currentUserId, Long secondSignerId) {
        // 同上双签校验
        activeCache.invalidate("active");
        return updated;
    }

    @Transactional
    @AuditLog(eventType="DELETE", entityType="PRODUCT", operation="删除产品",
              requireDoubleSign=true)
    public void delete(Long id, Long currentUserId, Long secondSignerId) {
        // 软删除（@TableLogic 自动 is_deleted=true）
        // 触发器 trg_prevent_hard_delete 阻止物理 DELETE
        activeCache.invalidate("active");
    }
}
```

### 3.5 ProductController（API 设计 — 8 个端点）

```java
@RestController
@RequestMapping("/products")
@Tag(name = "产品管理")
public class ProductController {

    @GetMapping
    public Result<PageResult<Product>> list(
        @RequestParam(required=false) String keyword,
        @RequestParam(required=false) String productLine,
        @RequestParam(required=false) String status,
        @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size)

    @GetMapping("/all")   // 下拉框使用，R199 加缓存
    public Result<List<Product>> listAllActive()

    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable Long id)

    @PostMapping
    @AuditLog(eventType="CREATE", entityType="PRODUCT", operation="创建产品", requireDoubleSign=true)
    public Result<Product> create(@RequestBody ProductCreateRequest req,
                                   @RequestHeader("X-Second-Signer-Id") Long secondSignerId)

    @PutMapping("/{id}")
    @AuditLog(eventType="MODIFY", entityType="PRODUCT", operation="编辑产品", requireDoubleSign=true)
    public Result<Product> update(@PathVariable Long id,
                                   @RequestBody ProductUpdateRequest req,
                                   @RequestHeader("X-Second-Signer-Id") Long secondSignerId)

    @DeleteMapping("/{id}")
    @AuditLog(eventType="DELETE", entityType="PRODUCT", operation="删除产品", requireDoubleSign=true)
    public Result<Void> delete(@PathVariable Long id,
                                @RequestHeader("X-Second-Signer-Id") Long secondSignerId)

    // R199 v1.62：Excel 导出（POI 复用 R175 模式）
    @GetMapping("/export")
    public void exportExcel(HttpServletResponse response,
                             @RequestParam(required=false) String keyword,
                             @RequestParam(required=false) String productLine) {
        productExportService.export(response, keyword, productLine);
    }
}
```

### 3.6 DTO 完整版

#### 3.6.1 ProductCreateRequest
```java
@Data
public class ProductCreateRequest {
    @NotBlank private String productCode;   // 如 8333
    @NotBlank private String productName;   // 如 8333 多参数监护仪
    private String productLine;             // 字典 product_line
    @Pattern(regexp = "ACTIVE|DISCONTINUED|DEVELOPMENT")
    private String status = "ACTIVE";
    private String description;
}
```

#### 3.6.2 ProductUpdateRequest（产品编码不可改）
```java
@Data
public class ProductUpdateRequest {
    @NotBlank private String productName;
    private String productLine;
    @Pattern(regexp = "ACTIVE|DISCONTINUED|DEVELOPMENT")
    private String status;
    private String description;
}
```

#### 3.6.3 ProductResponse（含时间戳）
```java
@Data
public class ProductResponse {
    private Long id;
    private String productCode;
    private String productName;
    private String productLine;
    private String status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### 3.6.4 PageQuery（通用）
```java
@Data
public class PageQuery {
    @Min(0) private int page = 0;
    @Min(1) @Max(100) private int size = 20;
}
```

### 3.7 现有实体修改

| 实体文件 | 新增字段 | 类型 |
|----------|----------|------|
| `Requirement.java` | `productId` | `Long` |
| `RequirementPool.java` | `productId` | `Long` |
| `Project.java` | `productId` | `Long` |

### 3.8 现有 Service 修改

| Service | 改动 |
|---------|------|
| `RequirementPoolService.addToPool()` | 新增 `productId` 参数，写入表 |
| `RequirementService.createRequirement()` | 接收 `productId`（来自 Request body），写入表 |
| `ProjectService.create()` / `update()` | `productId` 字段正常传输 |

### 3.9 跨模块依赖（R199 决策：JdbcTemplate 跨 schema）

**R199 修复**：v1.0 设计 `med-rms-project → med-rms-product` 违反"跨模块查询必须 JdbcTemplate"项目铁律。

**新设计**：
- `med-rms-project` **不依赖** `med-rms-product`
- 通过 `ProjectProductNameResolver` 工具类（位于 `med-rms-project`）用 `JdbcTemplate` 跨 schema 查询产品名

```java
@Component
public class ProjectProductNameResolver {
    private final JdbcTemplate jdbc;
    private static final String SQL =
        "SELECT product_code || ' ' || product_name " +
        "FROM prd_schema.t_product WHERE id = ? AND NOT is_deleted";

    public String resolve(Long productId) {
        if (productId == null) return null;
        try {
            return jdbc.queryForObject(SQL, String.class, productId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
```

### 3.10 RBAC 权限（R199 新增）

PermissionMatrix 新增 6 条 product:* 权限：

| 权限码 | 角色 |
|--------|------|
| `product:list` | 全部角色（含 VIEWER） |
| `product:view` | 全部角色 |
| `product:create` | PD / QA_MGR / PM / ADMIN |
| `product:update` | PD / ADMIN |
| `product:delete` | ADMIN only |
| `product:export` | PD / QA_MGR / PM / ADMIN |

---

## 4. 前端设计

### 4.1 新增文件

| 文件 | 说明 |
|------|------|
| `src/api/product.ts` | API 层 |
| `src/components/ProductSelector.vue` | 产品下拉选择器（通用组件，参考 ProjectSelector） |
| `src/views/product/ProductList.vue` | 产品管理列表页（含新增/编辑弹窗、停用/启用、删除、Excel 导出） |

### 4.2 src/api/product.ts

```ts
export interface Product {
  id?: number
  productCode: string
  productName: string
  productLine?: string
  status?: 'ACTIVE' | 'DISCONTINUED' | 'DEVELOPMENT'
  description?: string
  createdAt?: string
  updatedAt?: string
}

export const productApi = {
  list(params?: { keyword?: string; productLine?: string; status?: string; page?: number; size?: number }): Promise<AxiosResponse<PageResult<Product>>>,
  all(): Promise<AxiosResponse<Product[]>>,
  get(id: number): Promise<AxiosResponse<Product>>,
  create(data: ProductCreateRequest, secondSignerId: number): Promise<AxiosResponse<Product>>,
  update(id: number, data: ProductUpdateRequest, secondSignerId: number): Promise<AxiosResponse<Product>>,
  delete(id: number, secondSignerId: number): Promise<AxiosResponse<void>>,
  export(params?: { keyword?: string; productLine?: string }): Promise<AxiosResponse<Blob>>,
}
```

### 4.3 ProductSelector 组件

```
路径: frontend/src/components/ProductSelector.vue
参考: ProjectSelector.vue（filterable + clearable 模式）

Props:
  modelValue: number | null    -- v-model 绑定
  placeholder: string          -- 默认 "请选择产品"
  syncToStore: boolean         -- 是否同步到 store（R192 模式）

Emits:
  update:modelValue
  change

数据源: GET /products/all（onMounted 加载，5min 缓存）
显示格式: productCode + productName（如 "8333 8333 多参数监护仪"）
```

### 4.4 产品管理页面（ProductList.vue）

布局：
- 顶部：搜索栏（产品编码 + 产品名称 + 产品线下拉 + 状态下拉 + 查询/重置按钮 + Excel 导出按钮）
- 中部：表格（ID / 编码 / 名称 / 产品线 / 状态 / 描述 / 创建时间 / 操作）
- 操作列：编辑（弹窗）、停用/启用、删除
- 右上角：「新增产品」按钮 → 弹出新增弹窗

新增/编辑弹窗字段：
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| 产品编码 | input | 是（新增）/ 不可改（编辑） | 如 8333 |
| 产品名称 | input | 是 | 如 8333 多参数监护仪 |
| 产品线 | select（字典 product_line） | 否 | 从 API /dicts?type=product_line 加载 |
| 状态 | select（ACTIVE / DEVELOPMENT / DISCONTINUED） | 否 | 默认 ACTIVE |
| 描述 | textarea | 否 | |

**R199 双签 UI**：所有写操作弹窗底部加「第二签名人」选择器（从 `/admin/users?role=ADMIN,PD` 加载）。

### 4.5 修改前端文件

| 文件 | 改动 | 备注 |
|------|------|------|
| `router/index.ts` | 加路由 `/products` → ProductList.vue（与 `/projects` 同级） | R199 |
| `src/views/project/ProjectCreate.vue` | 表单加「主产品」ProductSelector | R199 |
| `src/views/project/ProjectEdit.vue` | 表单加「主产品」ProductSelector（加载时回显） | R199 |
| `src/views/requirement/ReqCreate.vue` | 第 108-114 行硬编码下拉 → ProductSelector + `formData.productId` 随提交发送 | R199 |
| `src/views/requirement/RequirementPool.vue` | 新增/详情对话框加「适用产品」ProductSelector（`addForm` + `detailItem`） | R199 |
| `src/api/project.ts` | 接口加 `productId` 字段 | R199 |
| `src/App.vue` | 菜单加「产品管理」入口 | R199 |

### 4.6 全量硬编码排查清单（R199 实施前必做）

```bash
grep -rn "'心电监护仪'\|'脉搏血氧仪'\|'8333'\|'iMEC15'" \
  Code/frontend/src/ Code/backend/med-rms-*/
```

预期发现位置（v1.0 已知）：
- ✅ ReqCreate.vue（已纳入修复）
- 🔍 ChangeRequest 创建页（待排查）
- 🔍 TestCase 创建页（待排查）
- 🔍 Baseline 创建页（待排查）
- 🔍 其他需求/测试创建入口（待排查）

---

## 5. POM 变更

### 5.1 parent pom.xml

```xml
<!-- modules 加 -->
<module>med-rms-product</module>

<!-- dependencyManagement 加 -->
<dependency>
    <groupId>com.zhutao.medrms</groupId>
    <artifactId>med-rms-product</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 5.2 med-rms-web/pom.xml

```xml
<dependency>
    <groupId>com.zhutao.medrms</groupId>
    <artifactId>med-rms-product</artifactId>
</dependency>
```

### 5.3 med-rms-project/pom.xml（R199 决策：不加依赖）

```
<!-- R199 修复：v1.0 设计加 med-rms-product 依赖违反项目铁律 -->
<!-- 改用 JdbcTemplate 跨 schema 查询，见 §3.9 -->
```

---

## 6. 影响范围总览

| 类别 | 新增 | 修改 | 合计 |
|------|------|------|------|
| DDL | 1（`r199_product_mgmt.sql`） | 0 | 1 |
| 后端 Java | 9（Controller/Service/Mapper/Entity/DTO×4/Export/Config） | 8（Requirement/Pool/Project 实体 + Service + Project pom + ProjectProductNameResolver） | 17 |
| 后端 POM | 1（`med-rms-product/pom.xml`） | 2（parent/web；project 不加） | 3 |
| 前端 Vue | 3（ProductList + ProductSelector + App.vue menu） | 6（router + ProjectCreate/Edit + ReqCreate + RequirementPool + project.ts） | 9 |
| 前端 API | 1（`product.ts`） | 1（`api/project.ts` 接口加 productId） | 2 |
| RBAC | 6（product:* 权限码） | 1（PermissionMatrix） | 7 |
| e2e 测试 | 1（test_product_mgmt_e2e.py） | 0 | 1 |
| 文档 | 0 | 4（详细设计 + OpenAPI + RBAC矩阵 + 偏差清单） | 4 |
| **总计** | **21** | **22** | **43** |

> R199 影响范围较 v1.0 的 24 项提升到 43 项（Excel 导出 + 双签 + 完整 DTO + RBAC + e2e）。

---

## 7. 验证计划（R199 实施后必跑）

| 项 | 工具 | 期望 |
|----|------|------|
| DDL 跑通 | `psql ... --single-transaction --ON_ERROR_STOP=1` | 无错误 |
| 双签约束 | `test_product_mgmt_e2e.py` 用例 1 | admin+admin 拒 SY0101；pm+qa_mgr 通过 |
| RBAC | 用例 2（re 角色访问 /products 403） | re 无 product:create 权限 |
| 软删除+重建 | 用例 3（删除 8333 → 重建 8333） | 通过（partial unique index） |
| 数据回填 | 用例 4（SELECT product_id FROM t_project WHERE name LIKE '%8333%'） | 不为 NULL |
| 跨模块解耦 | `mvn -pl med-rms-project compile` | 不需要 med-rms-product jar 存在 |
| Playwright e2e | `npx playwright test --reporter=list` | 全量通过 |
| 后端 8080 | `curl /api/auth/login` | HTTP 200 |

---

## 8. 术语表

| 术语 | 解释 |
|------|------|
| **产品（Product）** | 医疗器械设备型号（如 8333 多参数监护仪） |
| **项目（Project）** | 某产品的某版本研发组织工作（如 8333 v3.0 项目） |
| **需求（Requirement）** | 对产品的功能/性能/安全要求 |
| **双签（Double Sign）** | 21 CFR Part 11 §11.200 主数据修改需 2 个不同用户签名 |
| **record_hash** | R197 G17：SHA-256 记录校验和，触发器自动维护 |
| **TimedCache** | R198b v1.61 通用定时缓存工具类 |

---

## 9. 参考文档

- `Detailed/03-OpenAPI/med-rms-openapi.yaml`（产品 7 个端点）
- `Detailed/04-权限设计/RBAC矩阵.md`（product:* 权限行）
- `开发日志.md` R199 节点
- `CONTEXT.md` / `SESSION_SUMMARY.md`
- 架构-实现偏差与文档同步/架构-实现偏差清单.md（R199 增量）

---

## 10. 变更记录

| 版本 | 日期 | 内容 | 修订人 |
|------|------|------|--------|
| v1.0 | 2026-07-21 | 初稿（评审前） | Claude |
| v1.1 | 2026-07-21 | 应用评审 18 项修复：字典小写 / 合规触发器 / record_hash / partial index / CHECK / TimedCache / 完整 DTO / 全量硬编码排查 / JdbcTemplate 跨 schema / 数据迁移 SQL / RBAC 6 条 / Excel 导出 / 双签约束 / e2e 5 用例 / OpenAPI 同步 / RBAC 矩阵同步 / 验证计划 / 术语表 | Claude |
package com.zhutao.medrms.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.compliance.domain.entity.DashboardConfig;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface DashboardConfigMapper extends BaseMapper<DashboardConfig> {

    /**
     * 用 ::text cast 把 jsonb 列转成字符串返回，避免 PG type handler 在 resultMap 中的处理问题。
     * 然后在 service 层通过 Jackson 反序列化为 List。
     */
    @Select("""
        SELECT id, user_id, layout_json::text AS layout_json, widgets_json::text AS widgets_json,
               is_default, updated_at, created_at
        FROM report_schema.dashboard_config
        WHERE user_id = #{userId}
        ORDER BY is_default DESC, updated_at DESC
        LIMIT 1
        """)
    DashboardConfigRaw selectRawByUserId(Long userId);

    @Select("""
        SELECT id, user_id, layout_json::text AS layout_json, widgets_json::text AS widgets_json,
               is_default, updated_at, created_at
        FROM report_schema.dashboard_config
        WHERE is_default = TRUE
        LIMIT 1
        """)
    DashboardConfigRaw selectRawDefault();

    @Select("""
        SELECT id, user_id, layout_json::text AS layout_json, widgets_json::text AS widgets_json,
               is_default, updated_at, created_at
        FROM report_schema.dashboard_config
        ORDER BY updated_at DESC
        """)
    List<DashboardConfigRaw> selectRawAll();

    /**
     * 显式 ::jsonb cast 写入布局（绕开 MyBatis-Plus 默认 String 处理与 PG jsonb 的类型冲突）
     */
    @Insert("""
        INSERT INTO report_schema.dashboard_config
        (user_id, layout_json, widgets_json, is_default, updated_at, created_at)
        VALUES
        (#{userId}, #{layoutJson}::jsonb, #{widgetsJson}::jsonb, #{isDefault}, #{updatedAt}, #{createdAt})
        """)
    int insertRaw(@Param("userId") Long userId,
                  @Param("layoutJson") String layoutJson,
                  @Param("widgetsJson") String widgetsJson,
                  @Param("isDefault") Boolean isDefault,
                  @Param("updatedAt") OffsetDateTime updatedAt,
                  @Param("createdAt") OffsetDateTime createdAt);

    @Update("""
        UPDATE report_schema.dashboard_config
        SET layout_json = #{layoutJson}::jsonb,
            widgets_json = #{widgetsJson}::jsonb,
            is_default = #{isDefault},
            updated_at = #{updatedAt}
        WHERE id = #{id}
        """)
    int updateRaw(@Param("id") Long id,
                  @Param("layoutJson") String layoutJson,
                  @Param("widgetsJson") String widgetsJson,
                  @Param("isDefault") Boolean isDefault,
                  @Param("updatedAt") OffsetDateTime updatedAt);

    /**
     * 用 Map 承载的 raw 行，避开 resultMap 类型处理
     */
    class DashboardConfigRaw {
        public Long id;
        public Long userId;
        public String layoutJson;
        public String widgetsJson;
        public Boolean isDefault;
        public OffsetDateTime updatedAt;
        public OffsetDateTime createdAt;
    }
}

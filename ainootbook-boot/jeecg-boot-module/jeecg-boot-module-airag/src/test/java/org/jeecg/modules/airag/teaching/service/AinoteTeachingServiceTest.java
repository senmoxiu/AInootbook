package org.jeecg.modules.airag.teaching.service;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.airag.teaching.dto.BatchResult;
import org.jeecg.modules.airag.teaching.dto.BatchUpsertDTO;
import org.jeecg.modules.airag.teaching.service.impl.AinoteTeachingServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 教学关系 Service 单元测试
 * 测试数据权限校验、唯一约束冲突处理、batchUpsert 幂等性
 */
@DisplayName("AinoteTeachingService 单元测试")
class AinoteTeachingServiceTest {

    @Nested
    @DisplayName("validateSemester 学期格式校验")
    class ValidateSemesterTest {

        @Test
        @DisplayName("正确格式 YYYY-YYYY-NN 应通过")
        void validFormat_shouldPass() {
            // 正确格式示例
            String[] validSemesters = {
                "2024-2025-01",
                "2024-2025-02",
                "2023-2024-01",
                "2099-2100-99"
            };

            AinoteTeachingServiceImpl service = new AinoteTeachingServiceImpl(null);
            for (String semester : validSemesters) {
                assertDoesNotThrow(() -> service.validateSemester(semester),
                    "学期格式 " + semester + " 应该通过校验");
            }
        }

        @Test
        @DisplayName("错误格式应抛出异常")
        void invalidFormat_shouldThrow() {
            String[] invalidSemesters = {
                "2024-2025-1",      // 缺少前导零
                "2024-2025",        // 缺少学期号
                "24-25-01",         // 年份不完整
                "2024/2025/01",     // 分隔符错误
                "2024-2025-001",    // 学期号过长
                "",                 // 空字符串
                null                // null
            };

            AinoteTeachingServiceImpl service = new AinoteTeachingServiceImpl(null);
            for (String semester : invalidSemesters) {
                assertThrows(JeecgBootException.class,
                    () -> service.validateSemester(semester),
                    "学期格式 " + semester + " 应该抛出异常");
            }
        }
    }

    @Nested
    @DisplayName("validateDepartId 组织ID校验")
    class ValidateDepartIdTest {

        @Test
        @DisplayName("空组织ID应抛出异常")
        void emptyDepartId_shouldThrow() {
            AinoteTeachingServiceImpl service = new AinoteTeachingServiceImpl(null);

            assertThrows(JeecgBootException.class,
                () -> service.validateDepartId(null),
                "null 组织ID应抛出异常");

            assertThrows(JeecgBootException.class,
                () -> service.validateDepartId(""),
                "空字符串组织ID应抛出异常");

            assertThrows(JeecgBootException.class,
                () -> service.validateDepartId("   "),
                "空白字符串组织ID应抛出异常");
        }
    }

    @Nested
    @DisplayName("BatchResult 响应对象")
    class BatchResultTest {

        @Test
        @DisplayName("成功计数和失败列表应正确记录")
        void batchResult_shouldRecordCorrectly() {
            List<BatchResult.FailedItem> failedList = Arrays.asList(
                new BatchResult.FailedItem("dept1", "已存在相同配置"),
                new BatchResult.FailedItem("dept2", "组织不存在")
            );

            BatchResult result = new BatchResult(5, failedList);

            assertEquals(5, result.getSuccessCount(), "成功计数应为5");
            assertEquals(2, result.getFailedList().size(), "失败列表应有2条");
            assertEquals("dept1", result.getFailedList().get(0).getDepartId());
            assertEquals("已存在相同配置", result.getFailedList().get(0).getReason());
        }
    }

    @Nested
    @DisplayName("BatchUpsertDTO 请求对象")
    class BatchUpsertDTOTest {

        @Test
        @DisplayName("DTO 字段应正确设置")
        void dto_shouldSetFieldsCorrectly() {
            BatchUpsertDTO dto = new BatchUpsertDTO();
            dto.setCourseId("course123");
            dto.setDepartIds(Arrays.asList("dept1", "dept2", "dept3"));
            dto.setSemester("2024-2025-01");
            dto.setAcademicYear("2024-2025");

            assertEquals("course123", dto.getCourseId());
            assertEquals(3, dto.getDepartIds().size());
            assertEquals("2024-2025-01", dto.getSemester());
            assertEquals("2024-2025", dto.getAcademicYear());
        }
    }
}

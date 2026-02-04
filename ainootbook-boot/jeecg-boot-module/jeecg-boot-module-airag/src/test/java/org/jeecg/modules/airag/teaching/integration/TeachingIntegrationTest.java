package org.jeecg.modules.airag.teaching.integration;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.JeecgSystemApplication;
import org.jeecg.modules.airag.teaching.dto.BatchResult;
import org.jeecg.modules.airag.teaching.dto.BatchUpsertDTO;
import org.jeecg.modules.airag.teaching.entity.AinoteCourse;
import org.jeecg.modules.airag.teaching.entity.AinoteTeaching;
import org.jeecg.modules.airag.teaching.service.IAinoteCourseService;
import org.jeecg.modules.airag.teaching.service.IAinoteTeachingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 教学模块集成测试
 * 需要启动 Spring 上下文和数据库连接
 *
 * 运行前提：
 * 1. 配置 application-test.yml 数据库连接
 * 2. 执行 Flyway 迁移脚本
 * 3. 准备测试数据（sys_depart 组织数据）
 */
@SpringBootTest(classes = JeecgSystemApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("需要配置测试环境后启用")
class TeachingIntegrationTest {

    @Autowired
    private IAinoteCourseService courseService;

    @Autowired
    private IAinoteTeachingService teachingService;

    private static String testCourseId;
    private static String testTeachingId;

    @Nested
    @DisplayName("11.5 课程库 CRUD 全流程")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CourseCrudTest {

        @Test
        @Order(1)
        @DisplayName("创建课程")
        @Transactional
        void createCourse() {
            AinoteCourse course = new AinoteCourse();
            course.setCourseName("测试课程");
            course.setCourseCode("TEST001");
            course.setCredit(3.0);
            course.setHours(48);
            course.setStatus(1);

            boolean saved = courseService.save(course);
            assertTrue(saved, "课程创建应成功");
            assertNotNull(course.getId(), "课程ID应自动生成");
            testCourseId = course.getId();
        }

        @Test
        @Order(2)
        @DisplayName("查询课程列表")
        void listCourses() {
            Page<AinoteCourse> page = new Page<>(1, 10);
            QueryWrapper<AinoteCourse> wrapper = new QueryWrapper<>();
            IPage<AinoteCourse> result = courseService.page(page, wrapper);

            assertNotNull(result, "查询结果不应为空");
            assertTrue(result.getTotal() >= 0, "总数应大于等于0");
        }

        @Test
        @Order(3)
        @DisplayName("更新课程")
        @Transactional
        void updateCourse() {
            if (testCourseId == null) {
                return; // 跳过，依赖前置测试
            }
            AinoteCourse course = courseService.getById(testCourseId);
            assertNotNull(course, "课程应存在");

            course.setCourseName("更新后的课程名");
            boolean updated = courseService.updateById(course);
            assertTrue(updated, "课程更新应成功");

            AinoteCourse updatedCourse = courseService.getById(testCourseId);
            assertEquals("更新后的课程名", updatedCourse.getCourseName());
        }

        @Test
        @Order(4)
        @DisplayName("删除课程（无引用）")
        @Transactional
        void deleteCourseWithoutReference() {
            // 创建一个新课程用于删除测试
            AinoteCourse course = new AinoteCourse();
            course.setCourseName("待删除课程");
            course.setCourseCode("DEL001");
            courseService.save(course);

            boolean deleted = courseService.removeWithProtection(course.getId());
            assertTrue(deleted, "无引用的课程应能删除");
        }

        @Test
        @Order(5)
        @DisplayName("删除课程（有引用）应失败")
        @Transactional
        void deleteCourseWithReference() {
            // 此测试需要先创建教学任务引用该课程
            // 实际测试时需要准备数据
        }
    }

    @Nested
    @DisplayName("11.6 教学任务 CRUD + 批量配置全流程")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class TeachingCrudTest {

        @Test
        @Order(1)
        @DisplayName("批量配置教学任务")
        @Transactional
        void batchUpsertTeaching() {
            // 需要模拟登录用户
            // 实际测试时需要配置 SecurityContext
        }

        @Test
        @Order(2)
        @DisplayName("批量配置幂等性测试")
        @Transactional
        void batchUpsertIdempotent() {
            // 重复调用 batchUpsert 应返回已存在的记录在 failedList 中
        }

        @Test
        @Order(3)
        @DisplayName("查询教学任务列表")
        void listTeaching() {
            Page<AinoteTeaching> page = new Page<>(1, 10);
            QueryWrapper<AinoteTeaching> wrapper = new QueryWrapper<>();
            IPage<AinoteTeaching> result = teachingService.page(page, wrapper);

            assertNotNull(result, "查询结果不应为空");
        }

        @Test
        @Order(4)
        @DisplayName("批量删除教学任务")
        @Transactional
        void batchDeleteTeaching() {
            // 需要模拟登录用户和数据权限
        }
    }

    @Nested
    @DisplayName("11.7 教师数据权限隔离")
    class TeacherPermissionTest {

        @Test
        @DisplayName("教师只能查看自己的教学任务")
        void teacherCanOnlySeeOwnTasks() {
            // 需要模拟教师角色登录
            // 验证 applyDataPermission 方法正确过滤 teacher_id
        }

        @Test
        @DisplayName("教师不能修改他人的教学任务")
        void teacherCannotModifyOthersTasks() {
            // 需要模拟教师角色登录
            // 尝试修改其他教师的任务应失败
        }

        @Test
        @DisplayName("管理员可以查看所有教学任务")
        void adminCanSeeAllTasks() {
            // 需要模拟管理员角色登录
            // 验证不受 teacher_id 过滤限制
        }
    }

    @Nested
    @DisplayName("11.8 多租户隔离")
    class TenantIsolationTest {

        @Test
        @DisplayName("租户A不能访问租户B的数据")
        void tenantIsolation() {
            // 需要模拟不同租户登录
            // 验证 tenant_id 过滤正确生效
        }

        @Test
        @DisplayName("唯一约束包含租户ID")
        void uniqueConstraintIncludesTenantId() {
            // 验证不同租户可以创建相同的教学任务配置
            // 同一租户不能创建重复配置
        }
    }
}

CREATE TABLE `edu_course_enrollment` (
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `student_user_id` BIGINT NOT NULL COMMENT '学生用户ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ENROLLED' COMMENT '状态: ENROLLED',
    `enrolled_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入课程时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`course_id`, `student_user_id`),
    KEY `idx_edu_course_enrollment_student` (`student_user_id`, `course_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程学生关系';

INSERT INTO `edu_course_enrollment` (`course_id`, `student_user_id`)
SELECT DISTINCT activity.course_id, activity.student_user_id
FROM (
    SELECT course_id, student_user_id FROM edu_learning_progress
    UNION
    SELECT course_id, student_user_id FROM edu_student_mastery
    UNION
    SELECT course_id, student_user_id FROM edu_learning_path
    UNION
    SELECT course_id, student_user_id FROM edu_generated_resource
    UNION
    SELECT assessment.course_id, attempt.student_user_id
    FROM edu_assessment_attempt attempt
    JOIN edu_assessment assessment ON assessment.id = attempt.assessment_id
) activity
JOIN edu_course course ON course.id = activity.course_id AND course.is_deleted = 0
JOIN edu_user_role role ON role.user_id = activity.student_user_id AND role.education_role = 'STUDENT'
ON DUPLICATE KEY UPDATE status = 'ENROLLED';

INSERT INTO `edu_course_enrollment` (`course_id`, `student_user_id`)
SELECT course.id, student.user_id
FROM edu_course course
JOIN edu_user_role student ON student.user_id = 8220002 AND student.education_role = 'STUDENT'
WHERE course.id = 8100001 AND course.is_deleted = 0
ON DUPLICATE KEY UPDATE status = 'ENROLLED';

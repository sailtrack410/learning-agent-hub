INSERT INTO org_department (
    id, parent_id, tree_path, dept_code, dept_name, dept_type, dept_level,
    status, created_by, updated_by, is_deleted
) VALUES
    (8200001, NULL, '/8200001/', 'SOFTWARE', '软件学院', 'COMPANY', 0, 'ACTIVE', NULL, NULL, 0),
    (8200002, 8200001, '/8200001/8200002/', 'SE_2024_1', '软件工程 2024 级 1 班', 'TEAM', 1, 'ACTIVE', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    tree_path = VALUES(tree_path),
    dept_name = VALUES(dept_name),
    dept_type = VALUES(dept_type),
    dept_level = VALUES(dept_level),
    status = VALUES(status),
    is_deleted = VALUES(is_deleted);

INSERT INTO org_position (
    id, position_code, position_name, position_category, status, created_by, updated_by, is_deleted
) VALUES
    (8210001, 'COURSE_TEACHER', '课程教师', 'TEACHER', 'ACTIVE', NULL, NULL, 0),
    (8210002, 'COURSE_STUDENT', '学生', 'STUDENT', 'ACTIVE', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    position_name = VALUES(position_name),
    position_category = VALUES(position_category),
    status = VALUES(status),
    is_deleted = VALUES(is_deleted);

INSERT INTO org_user (
    id, employee_no, real_name, gender, mobile, primary_dept_id, position_id, manager_user_id,
    employment_status, hire_date, service_start_date, annual_leave_start_date,
    work_location, status, created_by, updated_by, is_deleted
) VALUES
    (8220001, 'T2026001', '演示教师', NULL, NULL, 8200001, 8210001, NULL,
     'ACTIVE', '2026-01-01', '2026-01-01', '2026-01-01', '软件学院', 'ACTIVE', NULL, NULL, 0),
    (8220002, 'S2024001', '演示学生', NULL, NULL, 8200002, 8210002, 8220001,
     'ACTIVE', '2024-09-01', '2024-09-01', '2024-09-01', '软件工程 2024 级 1 班', 'ACTIVE', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    employee_no = VALUES(employee_no),
    real_name = VALUES(real_name),
    primary_dept_id = VALUES(primary_dept_id),
    position_id = VALUES(position_id),
    manager_user_id = VALUES(manager_user_id),
    employment_status = VALUES(employment_status),
    status = VALUES(status),
    is_deleted = VALUES(is_deleted);

INSERT INTO auth_user_account (
    id, user_id, login_name, password_hash, password_salt, email, email_verified_at,
    account_status, password_updated_at, created_by, updated_by, is_deleted
) VALUES
    (8230001, 8220001, 'teacher.demo', '$2a$10$CDmj/WrSdetJAbxwGsZnLOdVsia5y4KIrPkGz8NoEnDvCUX8Spx.G', NULL, 'teacher.demo@example.com', NOW(),
     'ACTIVE', NOW(), NULL, NULL, 0),
    (8230002, 8220002, 'student.demo', '$2a$10$CDmj/WrSdetJAbxwGsZnLOdVsia5y4KIrPkGz8NoEnDvCUX8Spx.G', NULL, 'student.demo@example.com', NOW(),
     'ACTIVE', NOW(), NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    login_name = VALUES(login_name),
    password_hash = VALUES(password_hash),
    email = VALUES(email),
    email_verified_at = VALUES(email_verified_at),
    account_status = VALUES(account_status),
    password_updated_at = VALUES(password_updated_at),
    is_deleted = VALUES(is_deleted);

INSERT INTO edu_user_role (id, user_id, education_role) VALUES
    (8240001, 8220001, 'TEACHER'),
    (8240002, 8220002, 'STUDENT')
ON DUPLICATE KEY UPDATE education_role = VALUES(education_role);

DELETE FROM admin_role_permission
WHERE permission_id IN (
    SELECT id FROM admin_permission WHERE perm_code = 'ADMIN_COURSE_MANAGE'
);

DELETE FROM admin_permission WHERE perm_code = 'ADMIN_COURSE_MANAGE';

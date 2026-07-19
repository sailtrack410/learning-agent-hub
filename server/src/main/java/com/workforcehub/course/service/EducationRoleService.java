package com.workforcehub.course.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.course.repository.EducationRoleRepository;
import org.springframework.stereotype.Service;

@Service
public class EducationRoleService {
    private final EducationRoleRepository educationRoleRepository;

    public EducationRoleService(EducationRoleRepository educationRoleRepository) {
        this.educationRoleRepository = educationRoleRepository;
    }

    public Long requireCurrentRole(String requiredRole) {
        Long userId = CurrentUserHolder.requireUserId();
        if (!requiredRole.equals(educationRoleRepository.findRoleByUserId(userId))) {
            throw new BizException(403, "当前账号无权访问该教育功能");
        }
        return userId;
    }
}

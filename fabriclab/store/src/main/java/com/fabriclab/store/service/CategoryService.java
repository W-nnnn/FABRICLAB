package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fabriclab.store.entity.Category;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.mapper.CategoryMapper;
import com.fabriclab.store.model.CategoryType;
import org.springframework.stereotype.Service;

/**
 * 两级分类体系：一级为系列线，二级为成衣 / 配饰。
 * 编码全局唯一且创建后不修改；父子关系创建后不修改；同级名称唯一由服务层校验。
 */
@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public Category create(String code, Long parentId, String name, Integer sortOrder) {
        String trimmedCode = requireText(code, "分类编码不能为空");
        String trimmedName = requireText(name, "分类名称不能为空");
        int sort = normalizeSort(sortOrder);
        requireCodeUnique(trimmedCode, null);
        if (parentId == null) {
            requireSiblingNameUnique(trimmedName, null, null);
        } else {
            Category parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException("上级分类不存在");
            }
            if (parent.getParentId() != null) {
                throw new BusinessException("分类固定两级，上级必须是一级分类");
            }
            requireSiblingNameUnique(trimmedName, parent.getId(), null);
        }
        Category category = new Category();
        category.setCode(trimmedCode);
        category.setParentId(parentId);
        category.setName(trimmedName);
        category.setSortOrder(sort);
        categoryMapper.insert(category);
        return category;
    }

    /** 只允许改名称与排序；编码与父子关系创建后不可变，因此不在参数里。 */
    public Category update(Long id, String name, Integer sortOrder) {
        Category existing = requireExisting(id);
        String trimmedName = requireText(name, "分类名称不能为空");
        int sort = normalizeSort(sortOrder);
        requireSiblingNameUnique(trimmedName, existing.getParentId(), id);
        existing.setName(trimmedName);
        existing.setSortOrder(sort);
        categoryMapper.updateById(existing);
        return existing;
    }

    /** 类型三值由编码映射，全部 / 成衣 / 配饰。 */
    public String typeOf(Category category) {
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        return CategoryType.fromCode(category.getCode()).name();
    }

    private void requireCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<Category> query = new LambdaQueryWrapper<Category>()
                .eq(Category::getCode, code)
                .ne(excludeId != null, Category::getId, excludeId);
        if (categoryMapper.selectCount(query) > 0) {
            throw new BusinessException("分类编码已存在：" + code);
        }
    }

    private void requireSiblingNameUnique(String name, Long parentId, Long excludeId) {
        LambdaQueryWrapper<Category> query = new LambdaQueryWrapper<Category>()
                .eq(Category::getName, name)
                .eq(parentId == null, Category::getParentId, 0L)
                .eq(parentId != null, Category::getParentId, parentId)
                .ne(excludeId != null, Category::getId, excludeId);
        if (categoryMapper.selectCount(query) > 0) {
            throw new BusinessException("同级分类名称已存在：" + name);
        }
    }

    private Category requireExisting(Long id) {
        if (id == null) {
            throw new BusinessException("缺少分类 ID");
        }
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }
        return existing;
    }

    private int normalizeSort(Integer sortOrder) {
        int sort = sortOrder == null ? 0 : sortOrder;
        if (sort < 0) {
            throw new BusinessException("排序值不能为负");
        }
        return sort;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value.trim();
    }
}

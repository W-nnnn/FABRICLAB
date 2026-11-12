package com.fabriclab.store.controller;

import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.CsrfViolationException;
import com.fabriclab.store.exception.NotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 统一错误呈现：参数错误 400、资源不存在 404、技术异常 500。
 * 技术异常输出通用提示，不暴露 SQL、堆栈或内部信息。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public String handleBadRequest(BusinessException e, Model model, HttpServletResponse response) {
        return render(model, response, 400, "参数错误", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public String handleNotFound(NotFoundException e, Model model, HttpServletResponse response) {
        return render(model, response, 404, e.getTitle(), e.getMessage());
    }

    @ExceptionHandler(CsrfViolationException.class)
    public String handleCsrfViolation(Model model, HttpServletResponse response) {
        return render(model, response, 403, "无效表单防护请求", "表单校验未通过，请返回重新操作");
    }

    @ExceptionHandler(DataAccessException.class)
    public String handleDataAccess(Model model, HttpServletResponse response) {
        return render(model, response, 500, "加载失败", "数据暂时无法读取，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpected(Model model, HttpServletResponse response) {
        return render(model, response, 500, "服务暂时不可用", "发生未知错误，请稍后重试");
    }

    private String render(Model model, HttpServletResponse response, int status,
                          String title, String message) {
        response.setStatus(status);
        model.addAttribute("statusCode", status);
        model.addAttribute("errorTitle", title);
        model.addAttribute("errorMessage", message);
        return "error";
    }
}

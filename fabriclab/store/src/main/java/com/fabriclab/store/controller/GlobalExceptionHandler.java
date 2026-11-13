package com.fabriclab.store.controller;

import com.fabriclab.store.config.ContentI18n;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.CsrfViolationException;
import com.fabriclab.store.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.ui.Model;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一错误呈现：参数错误 400、资源不存在 404、技术异常 500。
 * 技术异常输出通用提示，不暴露 SQL、堆栈或内部信息。
 * 异常渲染时 @ControllerAdvice 的 @ModelAttribute 不会执行，
 * 因此语言链接与内容翻译器在此手动注入。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private final ContentI18n contentI18n = new ContentI18n();

    @ExceptionHandler(BusinessException.class)
    public String handleBadRequest(BusinessException e, Model model, HttpServletRequest request,
                                   HttpServletResponse response) {
        return render(model, request, response, 400, "参数错误", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public String handleNotFound(NotFoundException e, Model model, HttpServletRequest request,
                                 HttpServletResponse response) {
        return render(model, request, response, 404, e.getTitle(), e.getMessage());
    }

    @ExceptionHandler(CsrfViolationException.class)
    public String handleCsrfViolation(Model model, HttpServletRequest request,
                                      HttpServletResponse response) {
        return render(model, request, response, 403, "无效表单防护请求", "表单校验未通过，请返回重新操作");
    }

    @ExceptionHandler(DataAccessException.class)
    public String handleDataAccess(Model model, HttpServletRequest request,
                                   HttpServletResponse response) {
        return render(model, request, response, 500, "加载失败", "数据暂时无法读取，请稍后重试");
    }

    /** 未匹配到的地址：没有对应页面就是 404，不能落到通用 500。 */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public String handleMissingPath(Model model, HttpServletRequest request,
                                    HttpServletResponse response) {
        return render(model, request, response, 404, "页面不存在", "链接可能失效，或者这件已经卖完下架");
    }

    /** 访问方式不对（例如对只收表单的地址发 GET）：明确 405，不冒充服务故障。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public String handleMethodNotSupported(Model model, HttpServletRequest request,
                                           HttpServletResponse response) {
        return render(model, request, response, 405, "该地址不支持此访问方式", "请从页面上的入口重新操作");
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpected(Model model, HttpServletRequest request,
                                   HttpServletResponse response) {
        return render(model, request, response, 500, "服务暂时不可用", "发生未知错误，请稍后重试");
    }

    private String render(Model model, HttpServletRequest request, HttpServletResponse response,
                          int status, String title, String message) {
        response.setStatus(status);
        LangLinks.fill(model, request);
        model.addAttribute("tr", contentI18n);
        model.addAttribute("statusCode", status);
        model.addAttribute("errorTitle", title);
        model.addAttribute("errorMessage", message);
        return "error";
    }
}

package com.rvt.ticket.web;

import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class ErrorHandler {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    String uploadTooLarge(Model m){m.addAttribute("error","Ảnh QR quá lớn. Hãy chọn ảnh PNG/JPEG nhỏ hơn 4 MB.");return "error";}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    String invalid(IllegalArgumentException e,Model m) {m.addAttribute("error",e.getMessage());return "error";}
    @ExceptionHandler(DataAccessException.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    String database(DataAccessException e,Model m) {
        LoggerFactory.getLogger(ErrorHandler.class).error("Database operation failed ({})",e.getMostSpecificCause().getClass().getSimpleName());
        m.addAttribute("error","Dữ liệu tạm thời chưa khả dụng. Vui lòng thử lại sau.");return "error";
    }
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.beans.InvalidPropertyException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    String malformed(Model m){m.addAttribute("error","Thông tin trong đường dẫn hoặc biểu mẫu chưa đúng định dạng. Hãy mở lại trang và nhập thông tin hợp lệ.");return "error";}
}

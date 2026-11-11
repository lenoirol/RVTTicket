package com.rvt.ticket.web;
import java.util.*;
import org.springframework.validation.BindingResult;
public final class InputErrors {
    private InputErrors(){}
    public static Map<String,String> collect(BindingResult result){
        var errors=new LinkedHashMap<String,String>();
        for(var e:result.getFieldErrors())errors.putIfAbsent(e.getField(),switch(e.getCode()){
            case "NotBlank","NotNull","NotEmpty" -> "Vui lòng điền thông tin này.";
            case "Pattern","Email" -> "Thông tin chưa đúng định dạng.";
            case "Size" -> "Độ dài hoặc số lượng chưa phù hợp giới hạn.";
            case "Min","Max","DecimalMin","DecimalMax" -> "Giá trị chưa nằm trong giới hạn cho phép.";
            case "Future" -> "Thời gian phải ở trong tương lai.";
            default -> "Hãy kiểm tra lại thông tin này.";
        });
        return errors;
    }
}

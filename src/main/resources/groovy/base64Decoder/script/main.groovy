import groovy.json.JsonBuilder
import java.util.Base64
import java.nio.charset.StandardCharsets

def base64DecodeGroovy(String input) {
    if (input == null || input.trim().isEmpty()) {
        return new JsonBuilder([
            "error": "输入数据为空",
            "status": "failed",
            "timestamp": new Date().toString()
        ]).toPrettyString()
    }

    try {
        // 清理输入字符串，移除可能的空白字符和换行符
        String cleanInput = input.trim().replaceAll("\\s+", "")
        
        // 验证Base64格式
        if (!isValidBase64(cleanInput)) {
            return new JsonBuilder([
                "error": "输入不是有效的Base64格式",
                "status": "failed",
                "input": input,
                "timestamp": new Date().toString()
            ]).toPrettyString()
        }

        // 执行Base64解码
        byte[] decodedBytes = Base64.getDecoder().decode(cleanInput)
        String decodedString = new String(decodedBytes, StandardCharsets.UTF_8)
        
        return new JsonBuilder([
            "original_base64": input,
            "decoded_value": decodedString,
            "original_length": input.length(),
            "decoded_length": decodedBytes.length,
            "encoding": "UTF-8",
            "status": "success",
            "timestamp": new Date().toString()
        ]).toPrettyString()

    } catch (IllegalArgumentException e) {
        return new JsonBuilder([
            "error": "Base64解码失败，输入格式不正确: ${e.getMessage()}",
            "status": "failed",
            "input": input,
            "exception_type": e.getClass().getSimpleName(),
            "timestamp": new Date().toString()
        ]).toPrettyString()
    } catch (Exception e) {
        return new JsonBuilder([
            "error": "解码过程中发生异常: ${e.getMessage()}",
            "status": "failed",
            "input": input,
            "exception_type": e.getClass().getSimpleName(),
            "timestamp": new Date().toString()
        ]).toPrettyString()
    }
}

/**
 * 验证字符串是否为有效的Base64格式
 */
def isValidBase64(String input) {
    if (input == null || input.isEmpty()) {
        return false
    }
    
    // Base64字符集：A-Z, a-z, 0-9, +, /, =
    String base64Pattern = "^[A-Za-z0-9+/]*={0,2}\$"
    
    // 检查字符是否符合Base64字符集
    if (!input.matches(base64Pattern)) {
        return false
    }
    
    // 检查长度是否为4的倍数
    if (input.length() % 4 != 0) {
        return false
    }
    
    // 检查填充字符的位置
    int paddingCount = 0
    for (int i = input.length() - 1; i >= 0 && input.charAt(i) == '='; i--) {
        paddingCount++
    }
    
    // 填充字符不能超过2个
    return paddingCount <= 2
}

// 执行解码
return base64DecodeGroovy(inputString)

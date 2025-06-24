import cn.hutool.core.util.CharsetUtil
import cn.hutool.crypto.SmUtil
import cn.hutool.crypto.symmetric.SymmetricCrypto
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

def sm4DecryptGroovy(String input) {
    if (input == null || input.isEmpty()) {
        return [:]
    }

    try {
        // 使用固定密钥创建 SM4 加密器，密钥长度必须是16字节
        SymmetricCrypto sm4 = SmUtil.sm4("1234567890123456".getBytes())

        // 尝试解析输入为JSON数组
        def jsonSlurper = new JsonSlurper()
        def inputData

        try {
            inputData = jsonSlurper.parseText(input)
        } catch (Exception e) {
            // 如果不是JSON格式，按逗号分割字符串处理
            inputData = input.split(',').collect { it.trim() }
        }

        def result = [:]

        // 如果输入是数组或列表，批量解密
        if (inputData instanceof List) {
            inputData.eachWithIndex { encryptedData, index ->
                def key = "item_${index + 1}"
                if (encryptedData != null && !encryptedData.toString().isEmpty()) {
                    try {
                        def decryptedText = sm4.decryptStr(encryptedData.toString(), CharsetUtil.CHARSET_UTF_8)
                        result[key] = [
                            "encrypted": encryptedData.toString(),
                            "decrypted": decryptedText,
                            "status": "success"
                        ]
                    } catch (Exception ex) {
                        result[key] = [
                            "encrypted": encryptedData.toString(),
                            "decrypted": null,
                            "status": "failed",
                            "error": ex.getMessage()
                        ]
                    }
                } else {
                    result[key] = [
                        "encrypted": "",
                        "decrypted": null,
                        "status": "skipped",
                        "error": "Empty input"
                    ]
                }
            }
        } else {
            // 单个字符串解密
            try {
                def decryptedText = sm4.decryptStr(inputData.toString(), CharsetUtil.CHARSET_UTF_8)
                result["single"] = [
                    "encrypted": inputData.toString(),
                    "decrypted": decryptedText,
                    "status": "success"
                ]
            } catch (Exception ex) {
                result["single"] = [
                    "encrypted": inputData.toString(),
                    "decrypted": null,
                    "status": "failed",
                    "error": ex.getMessage()
                ]
            }
        }

        return new JsonBuilder(result).toPrettyString()

    } catch (Exception e) {
        return new JsonBuilder([
            "error": "Processing failed: " + e.getMessage(),
            "status": "failed"
        ]).toPrettyString()
    }
}

return sm4DecryptGroovy(inputString)

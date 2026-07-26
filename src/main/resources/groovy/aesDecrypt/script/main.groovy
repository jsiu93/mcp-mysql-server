import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.util.Base64

def aesDecryptGroovy(String input) {
    if (input == null || input.trim().isEmpty()) {
        return new JsonBuilder([
            "error": "输入数据为空",
            "status": "failed",
            "timestamp": new Date().toString()
        ]).toPrettyString()
    }

    try {
        // 获取AES密钥配置
        String aesKey = getAesKey()
        if (aesKey == null || aesKey.trim().isEmpty()) {
            return new JsonBuilder([
                "error": "AES密钥未配置，请在extension.yml中配置aesKey参数",
                "status": "failed",
                "timestamp": new Date().toString()
            ]).toPrettyString()
        }

        println("使用AES密钥进行解密操作")

        // 尝试解析输入为JSON数组或单个值
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
                if (encryptedData != null && !encryptedData.toString().trim().isEmpty()) {
                    try {
                        def decryptedText = aesDecrypt(encryptedData.toString().trim(), aesKey)
                        result[key] = [
                            "encrypted": encryptedData.toString(),
                            "decrypted": decryptedText,
                            "status": "success"
                        ]
                        println("成功解密第${index + 1}项数据")
                    } catch (Exception ex) {
                        result[key] = [
                            "encrypted": encryptedData.toString(),
                            "decrypted": null,
                            "status": "failed",
                            "error": ex.getMessage()
                        ]
                        println("解密第${index + 1}项数据失败: ${ex.getMessage()}")
                    }
                } else {
                    result[key] = [
                        "encrypted": "",
                        "decrypted": null,
                        "status": "skipped",
                        "error": "输入数据为空"
                    ]
                }
            }
        } else {
            // 单个字符串解密
            try {
                def decryptedText = aesDecrypt(inputData.toString().trim(), aesKey)
                result["single"] = [
                    "encrypted": inputData.toString(),
                    "decrypted": decryptedText,
                    "status": "success"
                ]
                println("成功解密单个数据项")
            } catch (Exception ex) {
                result["single"] = [
                    "encrypted": inputData.toString(),
                    "decrypted": null,
                    "status": "failed",
                    "error": ex.getMessage()
                ]
                println("解密单个数据项失败: ${ex.getMessage()}")
            }
        }

        return new JsonBuilder(result).toPrettyString()

    } catch (Exception e) {
        println("AES解密处理过程中发生异常: ${e.getMessage()}")
        return new JsonBuilder([
            "error": "处理失败: " + e.getMessage(),
            "status": "failed",
            "exception_type": e.getClass().getSimpleName(),
            "timestamp": new Date().toString()
        ]).toPrettyString()
    }
}

/**
 * 获取AES密钥配置
 */
def getAesKey() {
    try {
        if (extensionConfig != null && extensionConfig.containsKey("aesKey")) {
            return extensionConfig.get("aesKey").toString()
        }
        return null
    } catch (Exception e) {
        println("获取AES密钥配置失败: ${e.getMessage()}")
        return null
    }
}

/**
 * AES解密方法
 * 匹配客户端实现：使用十六进制编码，支持自定义算法实例
 */
def aesDecrypt(String encryptedText, String key) {
    try {
        // 获取算法实例配置，默认使用AES/ECB/PKCS5Padding
        String instance = getAesInstance()

        // 将密钥转换为字节数组
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8)
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES")

        // 创建解密器
        Cipher cipher = Cipher.getInstance(instance)
        cipher.init(Cipher.DECRYPT_MODE, secretKey)

        // 删除所有空白字符
        String cleanEncryptedText = encryptedText.replaceAll("\\s+", "")

        // 十六进制解码加密文本
        byte[] encryptedBytes = hexToBytes(cleanEncryptedText)

        // 执行解密
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes)

        // 返回解密后的字符串
        return new String(decryptedBytes, StandardCharsets.UTF_8)

    } catch (Exception e) {
        throw new RuntimeException("AES解密失败: " + e.getMessage(), e)
    }
}

/**
 * 获取AES算法实例配置
 */
def getAesInstance() {
    try {
        if (extensionConfig != null && extensionConfig.containsKey("aesInstance")) {
            return extensionConfig.get("aesInstance").toString()
        }
        return "AES/ECB/PKCS5Padding" // 默认算法实例
    } catch (Exception e) {
        println("获取AES算法实例配置失败: ${e.getMessage()}")
        return "AES/ECB/PKCS5Padding"
    }
}

/**
 * 十六进制字符串转字节数组
 */
def hexToBytes(String hexString) {
    if (hexString == null || hexString.length() % 2 != 0) {
        throw new IllegalArgumentException("无效的十六进制字符串")
    }

    int len = hexString.length()
    byte[] data = new byte[len / 2]

    for (int i = 0; i < len; i += 2) {
        int index = (int)(i / 2)  // 显式转换为int类型
        data[index] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                             + Character.digit(hexString.charAt(i + 1), 16))
    }

    return data
}

// 执行解密
return aesDecryptGroovy(inputString)

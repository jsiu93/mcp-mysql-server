import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.util.Base64
import java.nio.charset.StandardCharsets

def iotdbBase64DecodeGroovy(String input) {
    if (input == null || input.isEmpty()) {
        return [
            "error": "输入数据为空",
            "status": "failed"
        ]
    }

    try {
        def jsonSlurper = new JsonSlurper()
        def inputData
        
        // 尝试解析输入为JSON
        try {
            inputData = jsonSlurper.parseText(input)
        } catch (Exception e) {
            return [
                "error": "输入数据不是有效的JSON格式: ${e.getMessage()}",
                "status": "failed",
                "input": input
            ]
        }

        def result = [:]
        
        // 处理IoTDB查询结果
        if (inputData instanceof Map) {
            inputData.each { dataSourceName, records ->
                if (records instanceof List) {
                    def decodedRecords = []
                    
                    records.eachWithIndex { record, index ->
                        def decodedRecord = [:]
                        
                        record.each { fieldName, fieldValue ->
                            if (fieldValue instanceof Map && fieldValue.containsKey("values") && fieldValue.containsKey("length")) {
                                // 这是一个base64编码的字段
                                try {
                                    def base64Value = fieldValue.values
                                    def decodedBytes = Base64.getDecoder().decode(base64Value)
                                    def decodedString = new String(decodedBytes, StandardCharsets.UTF_8)
                                    
                                    decodedRecord[fieldName] = [
                                        "original_base64": base64Value,
                                        "decoded_value": decodedString,
                                        "length": fieldValue.length,
                                        "actual_decoded_length": decodedBytes.length,
                                        "status": "decoded"
                                    ]
                                } catch (Exception ex) {
                                    decodedRecord[fieldName] = [
                                        "original_base64": fieldValue.values,
                                        "decoded_value": null,
                                        "length": fieldValue.length,
                                        "error": ex.getMessage(),
                                        "status": "decode_failed"
                                    ]
                                }
                            } else {
                                // 非base64字段，直接保留
                                decodedRecord[fieldName] = fieldValue
                            }
                        }
                        
                        decodedRecords.add(decodedRecord)
                    }
                    
                    result[dataSourceName] = [
                        "total_records": records.size(),
                        "decoded_records": decodedRecords,
                        "status": "success"
                    ]
                } else {
                    result[dataSourceName] = [
                        "error": "数据源记录不是列表格式",
                        "status": "failed",
                        "data": records
                    ]
                }
            }
        } else {
            return [
                "error": "输入数据不是Map格式，无法处理IoTDB查询结果",
                "status": "failed",
                "input_type": inputData.getClass().getSimpleName()
            ]
        }

        return new JsonBuilder([
            "decoded_data": result,
            "processing_status": "success",
            "timestamp": new Date().toString()
        ]).toPrettyString()

    } catch (Exception e) {
        return new JsonBuilder([
            "error": "处理过程中发生异常: ${e.getMessage()}",
            "status": "failed",
            "exception_type": e.getClass().getSimpleName(),
            "timestamp": new Date().toString()
        ]).toPrettyString()
    }
}

return iotdbBase64DecodeGroovy(inputString)

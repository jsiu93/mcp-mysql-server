# AES解密插件使用说明

## 概述

AES解密插件已成功实现并集成到MCP MySQL服务器中。该插件支持使用十六进制编码的AES加密数据解密，完全匹配客户端的加密实现。

## 配置信息

### extension.yml配置
```yaml
- name: aesDecrypt
  description: "AES解密工具，用于解密AES加密的数据字段"
  prompt: "使用AES算法解密加密的数据。输入应为十六进制编码的AES加密字符串或包含多个加密字符串的JSON数组。工具会使用配置的密钥和算法实例进行解密，返回包含原始加密值和解密后明文的详细结果。支持AES-128/192/256，匹配客户端加密实现"
  config:
    aesKey: "your-32-byte-aes-key-goes-here!!"
    aesInstance: "AES/ECB/PKCS5Padding"
```

### 配置参数说明
- `aesKey`: AES解密密钥，与客户端加密使用的密钥保持一致
- `aesInstance`: AES算法实例，默认为"AES/ECB/PKCS5Padding"，与客户端保持一致

## 使用方法

### 1. 查询数据库获取加密字段
```sql
select sxj_password from t_camera where id = 8;
```

### 2. 使用AES解密插件
通过MCP工具调用：
- 工具名称: `executeGroovyScript`
- 扩展名称: `aesDecrypt`
- 输入数据: 十六进制编码的加密字符串

### 3. 支持的输入格式

#### 单个加密字符串
```
970258f5ea645c6a2c5350a672c13fb7
```

#### 多个加密字符串（JSON数组）
```json
["970258f5ea645c6a2c5350a672c13fb7", "另一个十六进制密文"]
```

#### 逗号分隔的字符串
```
970258f5ea645c6a2c5350a672c13fb7,另一个十六进制密文
```

## 输出格式

### 单个解密结果
```json
{
  "single": {
    "encrypted": "970258f5ea645c6a2c5350a672c13fb7",
    "decrypted": "demo-plaintext",
    "status": "success"
  }
}
```

### 多个解密结果
```json
{
  "item_1": {
    "encrypted": "970258f5ea645c6a2c5350a672c13fb7",
    "decrypted": "demo-plaintext",
    "status": "success"
  },
  "item_2": {
    "encrypted": "另一个密文",
    "decrypted": "解密结果",
    "status": "success"
  }
}
```

## 测试验证

使用密钥 `your-32-byte-aes-key-goes-here!!` 和明文 `demo-plaintext`：
- 加密结果（十六进制）: `970258f5ea645c6a2c5350a672c13fb7`
- 解密结果: `demo-plaintext`

## 错误处理

插件包含完善的错误处理机制：
- 密钥未配置时返回错误信息
- 十六进制格式错误时返回详细错误
- 解密失败时返回具体错误原因
- 支持部分成功的批量解密

## 技术实现

- **编码方式**: 十六进制编码（匹配客户端实现）
- **算法支持**: AES-128/192/256
- **填充模式**: PKCS5Padding
- **工作模式**: ECB（可通过配置修改）
- **字符编码**: UTF-8

## 注意事项

1. 确保配置的密钥与客户端加密时使用的密钥完全一致
2. 输入的加密数据必须是有效的十六进制字符串
3. 算法实例配置必须与客户端加密时使用的实例一致
4. 插件会自动清理输入中的空白字符

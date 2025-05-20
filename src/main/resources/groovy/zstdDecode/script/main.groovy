import com.github.luben.zstd.Zstd
import java.nio.charset.StandardCharsets

def decompressGroovy(String compressed) {
    if (compressed == null || compressed.isEmpty()) {
        return null
    }
    byte[] compressedBytes = Base64.getDecoder().decode(compressed);
    byte[] decompressedData = new byte[(int) Zstd.decompressedSize(compressedBytes)];
    Zstd.decompress(decompressedData, compressedBytes);
    return new String(decompressedData, StandardCharsets.UTF_8);
}

return decompressGroovy(inputString)

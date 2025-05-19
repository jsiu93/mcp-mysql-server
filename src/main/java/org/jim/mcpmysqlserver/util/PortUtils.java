package org.jim.mcpmysqlserver.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * 端口工具类，用于检测端口是否被占用
 * @author yangxin
 */
@Slf4j
public class PortUtils {

    /**
     * 检查指定端口是否可用（未被占用）
     * @param port 要检查的端口号
     * @return 如果端口可用返回true，否则返回false
     */
    public static boolean isPortAvailable(int port) {
        if (port <= 0) {
            log.warn("Invalid port number: {}", port);
            return false;
        }

        try (ServerSocket ignored = new ServerSocket(port)) {
            // 尝试绑定端口，如果能绑定成功则说明端口可用
            log.debug("Port {} is available", port);
            return true;
        } catch (IOException e) {
            // 如果绑定失败，说明端口已被占用
            log.debug("Port {} is already in use: {}", port, e.getMessage());
            return false;
        }
        // 确保关闭ServerSocket释放端口
    }

    /**
     * 检查指定端口是否已被占用
     * @param port 要检查的端口号
     * @return 如果端口已被占用返回true，否则返回false
     */
    public static boolean isPortInUse(int port) {
        return !isPortAvailable(port);
    }

    /**
     * 获取一个可用的随机端口
     * @return 可用的随机端口号，如果无法获取则返回-1
     */
    public static int findAvailablePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            log.debug("Found available port: {}", port);
            return port;
        } catch (IOException e) {
            log.error("Failed to find available port: {}", e.getMessage(), e);
            return -1;
        }
    }
}

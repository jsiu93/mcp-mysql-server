package org.jim.mcpmysqlserver.config.extension;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author James Smith
 */
@Data
@Accessors(chain = true)
public class Extension {

    /**
     * 扩展名
     */
    private String name;

    /**
     * 启用状态，默认启用。true-启用 false-禁用
     */
    private Boolean enabled = true;

    /**
     * 脚本内容。（脚本内容与脚本路径都会被执行）
     */
    private String script;

    /**
     * 主函数文件名。默认值为：main.groovy
     */
    private String mainFileName = "main.groovy";

    /**
     * 脚本加载路径。默认为：resources/扩展名/groovy/script （脚本内容与脚本路径都会被执行）
     * <br>
     * TODO 目前暂不支持自定义加载路径。
     */
    private String scriptPath;

    /**
     * groovy脚本依赖包加载路径。默认为：resources/扩展名/groovy/dependency
     * <br>
     * TODO 目前暂不支持自定义加载路径。
     */
    private String scriptPathDependency;

    /**
     * 描述
     */
    private String description;

    /**
     * 给AI模型的提示词
     */
    private String prompt;

}

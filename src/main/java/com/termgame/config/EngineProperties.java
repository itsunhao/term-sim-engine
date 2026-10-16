package com.termgame.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 引擎配置：游戏安装文件目录与存档目录。
 */
@ConfigurationProperties(prefix = "termgame")
public class EngineProperties {

    /** 游戏安装文件（JSON）所在目录 */
    private String gamesDir = "./games";

    /** 存档所在目录 */
    private String savesDir = "./saves";

    public String getGamesDir() {
        return gamesDir;
    }

    public void setGamesDir(String gamesDir) {
        this.gamesDir = gamesDir;
    }

    public String getSavesDir() {
        return savesDir;
    }

    public void setSavesDir(String savesDir) {
        this.savesDir = savesDir;
    }

    public Path gamesPath() {
        return Paths.get(gamesDir);
    }

    public Path savesPath() {
        return Paths.get(savesDir);
    }
}

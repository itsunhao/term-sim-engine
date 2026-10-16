package com.termgame.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.termgame.config.EngineProperties;
import com.termgame.model.def.GameDefinition;
import com.termgame.util.GameException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 游戏目录：负责扫描 /games 目录中的 .json 安装文件、解析并校验游戏定义。
 */
@Component
public class GameCatalog {

    private static final Logger log = LoggerFactory.getLogger(GameCatalog.class);

    private final EngineProperties props;
    private final ObjectMapper mapper;
    private final Map<String, GameDefinition> games = new ConcurrentHashMap<>();

    public GameCatalog(EngineProperties props, ObjectMapper gameObjectMapper) {
        this.props = props;
        this.mapper = gameObjectMapper;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** 重新扫描游戏目录 */
    public synchronized void reload() {
        games.clear();
        Path dir = props.gamesPath();
        if (!Files.isDirectory(dir)) {
            log.warn("游戏目录不存在: {}", dir.toAbsolutePath());
            return;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(p -> p.toString().toLowerCase().endsWith(".json"))
                    .sorted()
                    .forEach(p -> {
                        try {
                            GameDefinition def = load(p);
                            games.put(def.id(), def);
                            log.info("已加载游戏: {} v{} ({})", def.name(), def.version(), p.getFileName());
                        } catch (Exception e) {
                            log.error("加载游戏文件失败: {} - {}", p.getFileName(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("扫描游戏目录失败", e);
        }
    }

    public GameDefinition load(Path file) throws IOException {
        String json = Files.readString(file);
        GameDefinition def = mapper.readValue(json, GameDefinition.class);
        validate(def, file.getFileName().toString());
        return def;
    }

    /** 安装一个游戏 JSON 内容（写入目录并注册） */
    public synchronized GameDefinition install(String json, String suggestedFileName) {
        GameDefinition def;
        try {
            def = mapper.readValue(json, GameDefinition.class);
        } catch (Exception e) {
            throw new GameException("游戏文件解析失败：" + e.getMessage());
        }
        validate(def, suggestedFileName);
        Path dir = props.gamesPath();
        try {
            Files.createDirectories(dir);
            String safe = def.id().replaceAll("[^A-Za-z0-9_-]", "_");
            Path target = dir.resolve(safe + ".json");
            Files.writeString(target, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(def));
            games.put(def.id(), def);
            return def;
        } catch (IOException e) {
            throw new GameException("游戏文件保存失败：" + e.getMessage());
        }
    }

    /** 安装文件到目录（用于默认游戏预置） */
    public synchronized void installIfAbsent(String resourceJson, String fileName) {
        Path dir = props.gamesPath();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            if (Files.exists(target)) {
                return;
            }
            Files.writeString(target, resourceJson);
            GameDefinition def = load(target);
            games.put(def.id(), def);
            log.info("预置游戏已安装: {}", def.name());
        } catch (IOException e) {
            log.error("预置游戏安装失败: {}", fileName, e);
        }
    }

    public List<GameDefinition> listAll() {
        List<GameDefinition> list = new ArrayList<>(games.values());
        list.sort(Comparator.comparing(GameDefinition::name));
        return list;
    }

    public GameDefinition get(String id) {
        GameDefinition def = games.get(id);
        if (def == null) {
            throw new GameException("未找到游戏：" + id + "（请先安装对应的游戏文件）");
        }
        return def;
    }

    private void validate(GameDefinition def, String fileName) {
        // id 命名
        if (!def.id().matches("[A-Za-z0-9_-]+")) {
            throw new GameException("游戏 [" + fileName + "] 的 id 只能包含字母、数字、下划线和连字符");
        }
        // 商品 id 唯一
        Map<String, Long> pidCount = def.products().stream()
                .collect(java.util.stream.Collectors.groupingBy(p -> p.id(), java.util.stream.Collectors.counting()));
        pidCount.forEach((pid, cnt) -> {
            if (cnt > 1) throw new GameException("游戏 [" + def.id() + "] 存在重复商品 id：" + pid);
        });
        // 起始季节存在
        if (def.seasons().stream().noneMatch(s -> s.id().equals(def.meta().startSeason()))) {
            throw new GameException("游戏 [" + def.id() + "] 的 start_season 不存在：" + def.meta().startSeason());
        }
        // 装修等级必须连续从 2 开始
        List<Integer> levels = def.upgrades().stream().map(u -> u.level()).sorted().toList();
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i) != i + 2) {
                throw new GameException("游戏 [" + def.id() + "] 装修等级必须从 2 开始且连续递增");
            }
        }
        // 商品解锁引用的装修必须存在
        for (var p : def.products()) {
            if (p.unlockUpgradeId() != null && def.upgrade(p.unlockUpgradeId()) == null) {
                throw new GameException("游戏 [" + def.id() + "] 商品 " + p.id() + " 引用了不存在的装修 " + p.unlockUpgradeId());
            }
        }
    }
}

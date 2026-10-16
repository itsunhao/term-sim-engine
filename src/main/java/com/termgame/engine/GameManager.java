package com.termgame.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.termgame.config.EngineProperties;
import com.termgame.model.def.GameDefinition;
import com.termgame.model.state.GameState;
import com.termgame.repo.GameCatalog;
import com.termgame.util.GameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 存档管理：新建游戏、保存/读取/删除存档、列出存档摘要。
 */
@Service
public class GameManager {

    private static final Logger log = LoggerFactory.getLogger(GameManager.class);

    private final EngineProperties props;
    private final ObjectMapper mapper;
    private final GameCatalog catalog;
    private final GameEngine engine;

    public GameManager(EngineProperties props, ObjectMapper gameObjectMapper, GameCatalog catalog, GameEngine engine) {
        this.props = props;
        this.mapper = gameObjectMapper;
        this.catalog = catalog;
        this.engine = engine;
    }

    public static String sanitizeName(String name) {
        if (name == null || name.isBlank()) return "存档1";
        String cleaned = name.replaceAll("[^\\p{Alnum}\\u4e00-\\u9fa5_-]", "_").trim();
        if (cleaned.isEmpty()) return "存档1";
        return cleaned.length() > 30 ? cleaned.substring(0, 30) : cleaned;
    }

    public static String saveId(String gameId, String saveName) {
        return gameId + "::" + sanitizeName(saveName);
    }

    /** 新建游戏并立即保存 */
    public GameState newGame(String gameId, String saveName) {
        GameDefinition def = catalog.get(gameId);
        GameState state = engine.newGame(def, sanitizeName(saveName));
        save(state);
        return state;
    }

    public void save(GameState state) {
        try {
            Files.createDirectories(saveDir(state.getGameId()));
            Path file = saveFile(state.getGameId(), state.getSaveName());
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
        } catch (IOException e) {
            throw new GameException("存档写入失败：" + e.getMessage());
        }
    }

    public GameState load(String gameId, String saveName) {
        Path file = saveFile(gameId, saveName);
        if (!Files.exists(file)) {
            throw new GameException("存档不存在：" + gameId + "/" + saveName);
        }
        try {
            GameState state = mapper.readValue(Files.readString(file), GameState.class);
            // 校验存档对应的游戏仍然安装着
            GameDefinition def = catalog.get(state.getGameId());
            // 旧存档迁移 + 新字段兜底
            engine.resume(def, state);
            return state;
        } catch (IOException e) {
            throw new GameException("存档读取失败：" + e.getMessage());
        }
    }

    public void delete(String gameId, String saveName) {
        Path file = saveFile(gameId, saveName);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new GameException("存档删除失败：" + e.getMessage());
        }
    }

    /** 存档摘要（启动器列表用） */
    public List<SaveSummary> listSaves() {
        List<SaveSummary> out = new ArrayList<>();
        Path root = props.savesPath();
        if (!Files.isDirectory(root)) return out;
        try (Stream<Path> gameDirs = Files.list(root)) {
            gameDirs.filter(Files::isDirectory).forEach(gameDir -> {
                try (Stream<Path> files = Files.list(gameDir)) {
                    files.filter(p -> p.toString().toLowerCase().endsWith(".json"))
                            .forEach(file -> {
                                try {
                                    GameState s = mapper.readValue(Files.readString(file), GameState.class);
                                    GameDefinition def = null;
                                    try {
                                        def = catalog.get(s.getGameId());
                                    } catch (Exception ignore) {
                                        // 游戏可能已被卸载，摘要仍展示
                                    }
                                    // 旧格式存档迁移（仅内存中补齐新时间字段，正式迁移在 load 时落盘）
                                    if (def != null) {
                                        try {
                                            engine.resume(def, s);
                                        } catch (Exception ignore) {
                                            // 迁移失败不影响摘要展示
                                        }
                                    }
                                    out.add(new SaveSummary(
                                            s.getGameId(),
                                            s.getSaveName(),
                                            def == null ? s.getGameId() : def.name(),
                                            def == null ? "？" : def.icon(),
                                            s.getDate() == null ? "" : dayLabel(s, def),
                                            s.getCash(),
                                            s.getDebt(),
                                            s.getReputation(),
                                            s.isEnded(),
                                            s.getEndType(),
                                            file.toFile().lastModified()
                                    ));
                                } catch (Exception e) {
                                    log.warn("跳过损坏存档: {}", file.getFileName());
                                }
                            });
                } catch (IOException ignore) {
                }
            });
        } catch (IOException e) {
            log.warn("扫描存档目录失败", e);
        }
        out.sort(Comparator.comparingLong(SaveSummary::updatedAt).reversed());
        return out;
    }

    private String dayLabel(GameState s, GameDefinition def) {
        return "第" + s.dayOf() + "天 " + s.clockText();
    }

    private Path saveDir(String gameId) {
        return props.savesPath().resolve(gameId);
    }

    private Path saveFile(String gameId, String saveName) {
        return saveDir(gameId).resolve(sanitizeName(saveName) + ".json");
    }

    /** 存档摘要记录 */
    public record SaveSummary(
            String gameId,
            String saveName,
            String gameName,
            String gameIcon,
            String dayLabel,
            double cash,
            double debt,
            double reputation,
            boolean ended,
            String endType,
            long updatedAt
    ) {
        public String saveId() {
            return GameManager.saveId(gameId, saveName);
        }
    }
}

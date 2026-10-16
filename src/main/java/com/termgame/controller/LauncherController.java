package com.termgame.controller;

import com.termgame.engine.GameManager;
import com.termgame.model.def.GameDefinition;
import com.termgame.repo.GameCatalog;
import com.termgame.util.GameException;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 启动器页面：游戏安装 / 游戏列表 / 存档列表 / 进入游戏。
 */
@Controller
public class LauncherController {

    private static final Logger log = LoggerFactory.getLogger(LauncherController.class);

    private final GameCatalog catalog;
    private final GameManager manager;
    private final com.termgame.engine.GameEngine engine;

    public LauncherController(GameCatalog catalog, GameManager manager, com.termgame.engine.GameEngine engine) {
        this.catalog = catalog;
        this.manager = manager;
        this.engine = engine;
    }

    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        List<GameDefinition> games = catalog.listAll();
        model.addAttribute("games", games);
        model.addAttribute("saves", manager.listSaves());
        model.addAttribute("engineVersion", "1.10.0");
        Map<String, String> flash = (Map<String, String>) session.getAttribute("flash");
        if (flash != null) {
            model.addAttribute("flashType", flash.get("type"));
            model.addAttribute("flashMsg", flash.get("msg"));
            session.removeAttribute("flash");
        }
        return "launcher";
    }

    /** 安装游戏文件（JSON） */
    @PostMapping("/install")
    public String install(@RequestParam("file") MultipartFile file, HttpSession session) {
        try {
            if (file == null || file.isEmpty()) {
                throw new GameException("请选择要安装的游戏文件");
            }
            String json = new String(file.getBytes(), StandardCharsets.UTF_8);
            String name = file.getOriginalFilename() == null ? "game.json" : file.getOriginalFilename();
            GameDefinition def = catalog.install(json, name);
            flash(session, "success", "✔ 游戏安装成功：" + def.name() + " v" + def.version()
                    + "（" + def.products().size() + " 种商品 / " + def.npcs().size() + " 位 NPC）");
            log.info("游戏安装成功: {} v{}", def.name(), def.version());
        } catch (Exception e) {
            log.warn("游戏安装失败: {}", e.getMessage());
            flash(session, "error", "✘ 安装失败：" + e.getMessage());
        }
        return "redirect:/";
    }

    /** 新建游戏 */
    @PostMapping("/game/new")
    public String newGame(@RequestParam("gameId") String gameId,
                          @RequestParam(value = "saveName", defaultValue = "") String saveName,
                          HttpSession session) {
        try {
            GameManager.SaveSummary created = null;
            var state = manager.newGame(gameId, saveName);
            flash(session, "success", "✔ 新档已创建，开始经营吧！");
            return "redirect:/game/" + urlEncode(GameManager.saveId(gameId, state.getSaveName()));
        } catch (Exception e) {
            flash(session, "error", "✘ 创建失败：" + e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/game/{saveId:.+}")
    public String game(@PathVariable String saveId, Model model, HttpSession session) {
        try {
            String[] parts = saveId.split("::", 2);
            if (parts.length != 2) {
                throw new GameException("存档标识无效");
            }
            var state = manager.load(parts[0], parts[1]);
            GameDefinition def = catalog.get(state.getGameId());
            // 进入游戏前先按现实时间推进，保证初始状态时间最新
            engine.tick(def, state, System.currentTimeMillis());
            manager.save(state);
            model.addAttribute("saveId", saveId);
            model.addAttribute("state", state);
            model.addAttribute("def", def);
            model.addAttribute("stateJson", json(state));
            model.addAttribute("defJson", json(def));
            model.addAttribute("engineVersion", "1.10.0");
            return "game";
        } catch (Exception e) {
            flash(session, "error", "✘ 读取存档失败：" + e.getMessage());
            return "redirect:/";
        }
    }

    private String json(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
                    .writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void flash(HttpSession session, String type, String msg) {
        session.setAttribute("flash", Map.of("type", type, "msg", msg));
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}

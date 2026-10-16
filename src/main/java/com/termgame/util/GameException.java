package com.termgame.util;

/**
 * 引擎业务异常，message 会直接展示给玩家。
 */
public class GameException extends RuntimeException {

    public GameException(String message) {
        super(message);
    }

    public GameException(String message, Throwable cause) {
        super(message, cause);
    }
}

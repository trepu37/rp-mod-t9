package ru.rpautocomplete;

import org.lwjgl.glfw.GLFW;

/** Коды клавиш GLFW и их читаемые названия. */
public final class Keys {
    private Keys() {}

    public static final int ESCAPE = 256;
    public static final int ENTER = 257;
    public static final int TAB = 258;
    public static final int BACKSPACE = 259;
    public static final int DELETE = 261;
    public static final int RIGHT = 262;
    public static final int LEFT = 263;
    public static final int DOWN = 264;
    public static final int UP = 265;
    public static final int PAGE_UP = 266;
    public static final int PAGE_DOWN = 267;
    public static final int KP_ENTER = 335;

    public static final int MOD_SHIFT = 1;
    public static final int MOD_CONTROL = 2;
    public static final int MOD_ALT = 4;

    public static String name(int key) {
        switch (key) {
            case TAB: return "Tab";
            case UP: return "↑";
            case DOWN: return "↓";
            case LEFT: return "←";
            case RIGHT: return "→";
            case ENTER: return "Enter";
            case 32: return "Пробел";
            case DELETE: return "Delete";
            case BACKSPACE: return "Backspace";
            case 268: return "Home";
            case 269: return "End";
            case PAGE_UP: return "PageUp";
            case PAGE_DOWN: return "PageDown";
            default: break;
        }
        if (key >= 290 && key <= 301) return "F" + (key - 289);
        try {
            String n = GLFW.glfwGetKeyName(key, 0);
            if (n != null && !n.isEmpty()) return n.toUpperCase();
        } catch (Throwable ignored) {
            // GLFW может быть недоступен — используем запасное название
        }
        return "Клавиша " + key;
    }
}

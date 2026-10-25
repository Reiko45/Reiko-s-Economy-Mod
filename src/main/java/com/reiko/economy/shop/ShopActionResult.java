package com.reiko.economy.shop;

import net.minecraft.network.chat.Component;

/** Outcome of a shop action, with the message to show the player. */
public record ShopActionResult(boolean success, Component message) {
    public static ShopActionResult ok(Component message) {
        return new ShopActionResult(true, message);
    }

    public static ShopActionResult fail(Component message) {
        return new ShopActionResult(false, message);
    }

    public static ShopActionResult fail(String translationKey, Object... args) {
        return fail(Component.translatable(translationKey, args));
    }
}

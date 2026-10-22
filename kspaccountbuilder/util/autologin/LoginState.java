package net.runelite.client.plugins.microbot.kspaccountbuilder.util.autologin;

public enum LoginState
{
    IDLE,
    NO_CLIENT,
    PAUSED_FOR_KSP_BREAK,
    PAUSED_FOR_BREAK_HANDLER,
    WAITING_FOR_LOGIN,
    WAITING_FOR_LOGIN_PLAY_NOW,
    CLICKING_LOGIN_PLAY_NOW,
    WAITING_FOR_PLAY_BUTTON,
    CLICKING_PLAY,
    LOGGED_IN,
    ERROR
}

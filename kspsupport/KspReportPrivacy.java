package net.runelite.client.plugins.microbot.kspsupport;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.plugins.microbot.Microbot;

/** Redacts the current OSRS account from every KSP Support clipboard export. */
final class KspReportPrivacy
{
    private static final String MASK = "********";
    private static final Pattern ACCOUNT_FIELD = Pattern.compile(
            "(?i)\\b(accountHash|accountId|username|userName|displayName|rsn|playerName)\\s*[=:]\\s*[^\\s,|]+");
    private static final Pattern WINDOWS_HOME = Pattern.compile("(?i)[A-Z]:\\\\Users\\\\[^\\\\\\s]+");
    private static final Pattern UNIX_HOME = Pattern.compile("/(?:home|Users)/[^/\\s]+");
    private static final Pattern IP_ADDRESS = Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b");

    private KspReportPrivacy() {}

    static String redact(String input)
    {
        // Never put a raw report on the clipboard if the active identity
        // could not be read reliably.
        String name = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null)
                return null;
            return Microbot.getClient().getLocalPlayer().getName();
        }).orElse(null);
        if (name == null || name.trim().isEmpty())
            throw new IllegalStateException("Unable to verify active account; report not copied");

        long hash = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getAccountHash()).orElse(0L);
        if (hash == 0L)
            throw new IllegalStateException("Unable to verify active account hash; report not copied");
        String safe = Pattern.compile(Pattern.quote(name.trim()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(input).replaceAll(Matcher.quoteReplacement(MASK));
        if (hash != 0L)
        {
            safe = replaceNumber(safe, Long.toUnsignedString(hash));
            safe = replaceNumber(safe, Long.toString(hash));
        }
        safe = ACCOUNT_FIELD.matcher(safe).replaceAll("$1=" + MASK);
        safe = WINDOWS_HOME.matcher(safe).replaceAll(Matcher.quoteReplacement("[user-home]"));
        safe = UNIX_HOME.matcher(safe).replaceAll(Matcher.quoteReplacement("[user-home]"));
        return IP_ADDRESS.matcher(safe).replaceAll("[ip-redacted]");
    }

    private static String replaceNumber(String text, String number)
    {
        return Pattern.compile("(?<![0-9])" + Pattern.quote(number) + "(?![0-9])")
                .matcher(text).replaceAll(Matcher.quoteReplacement(MASK));
    }
}

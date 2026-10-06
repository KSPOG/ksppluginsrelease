package net.runelite.client.plugins.microbot.kspsupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Locale;

/** Bridges real Logback WARN/ERROR events into the KSP debug console. */
final class KspErrorLogBridge extends AppenderBase<ILoggingEvent>
{
    private static final String INTERNAL_LOGGER = "KSP.RuntimeDebug";
    private static final int MAX_STACK = 12_000;

    private final PluginManager pluginManager;
    private final KspDebugPanel panel;
    private Logger root;

    KspErrorLogBridge(PluginManager pluginManager, KspDebugPanel panel)
    {
        this.pluginManager = pluginManager;
        this.panel = panel;
        setName("KSP-Debug-Bridge");
    }

    void attach()
    {
        if (isStarted()) return;
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        setContext(root.getLoggerContext());
        start();
        root.addAppender(this);
    }

    void detach()
    {
        if (root != null) root.detachAppender(this);
        stop();
        root = null;
    }

    @Override
    protected void append(ILoggingEvent e)
    {
        try
        {
            if (panel == null || e == null || INTERNAL_LOGGER.equals(e.getLoggerName())) return;
            Level level = e.getLevel();
            if (!Level.ERROR.equals(level) && !Level.WARN.equals(level)) return;

            String plugin = matchPlugin(e);
            if (Level.WARN.equals(level) && plugin == null) return;
            if (plugin == null) plugin = "Client";

            StringBuilder msg = new StringBuilder("log | plugin=").append(plugin)
                    .append(" | logger=").append(e.getLoggerName())
                    .append(" | message=").append(e.getFormattedMessage());

            IThrowableProxy t = e.getThrowableProxy();
            if (t != null)
            {
                String stack = ThrowableProxyUtil.asString(t);
                if (stack.length() > MAX_STACK) stack = stack.substring(0, MAX_STACK) + "\n... stack trace truncated";
                msg.append('\n').append(stack);
            }

            panel.append(level.levelStr, msg.toString());
        }
        catch (Throwable ignored) { }
    }

    private String matchPlugin(ILoggingEvent event)
    {
        Collection<Plugin> plugins = pluginManager == null ? null : pluginManager.getPlugins();
        if (plugins == null) return null;

        String logger = event.getLoggerName() == null ? "" : event.getLoggerName();
        IThrowableProxy proxy = event.getThrowableProxy();
        String stack = proxy == null ? "" : ThrowableProxyUtil.asString(proxy);

        for (Plugin p : plugins)
        {
            if (p == null || p instanceof KspSupportPlugin || !isKsp(p)) continue;
            String cls = p.getClass().getName();
            int dot = cls.lastIndexOf('.');
            String pkg = dot > 0 ? cls.substring(0, dot) : cls;
            if (logger.startsWith(pkg) || stack.contains(cls) || stack.contains(pkg + ".")) return name(p);
        }
        return null;
    }

    private static boolean isKsp(Plugin p)
    {
        ClassLoader cl = p.getClass().getClassLoader();
        if (cl != null)
        {
            String n = cl.getClass().getSimpleName();
            if (n.contains("MemoryPluginClassLoader") || n.contains("PluginJarClassLoader")) return true;
        }
        PluginDescriptor d = p.getClass().getAnnotation(PluginDescriptor.class);
        if (d == null) return false;
        for (String a : d.authors()) if (a != null && a.toLowerCase(Locale.ROOT).contains("ksp")) return true;
        return d.name() != null && d.name().toLowerCase(Locale.ROOT).contains("ksp");
    }

    private static String name(Plugin p)
    {
        PluginDescriptor d = p.getClass().getAnnotation(PluginDescriptor.class);
        return d == null || d.name() == null || d.name().isEmpty() ? p.getClass().getSimpleName() : d.name();
    }
}

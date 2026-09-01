package net.runelite.client.plugins.microbot.kspaiofighter;

import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class KspAioFighterLevelTargetSettings
{
    private static final String ENABLED_KEY = "useLevelTargets";
    private static final String BACKUP_PREFIX = "levelTargetBackup.";

    private final ConfigManager configManager;
    private final KspAioFighterConfig config;

    @Inject
    KspAioFighterLevelTargetSettings(ConfigManager configManager, KspAioFighterConfig config)
    {
        this.configManager = configManager;
        this.config = config;
    }

    boolean isEnabled()
    {
        Boolean value = configManager.getConfiguration(KspAioFighterConfig.GROUP, ENABLED_KEY, Boolean.class);
        return value == null || value;
    }

    void setEnabled(boolean enabled)
    {
        configManager.setConfiguration(KspAioFighterConfig.GROUP, ENABLED_KEY, enabled);
    }

    void prepareForRun()
    {
        if (isEnabled())
        {
            restoreAfterRun();
            return;
        }

        backupIfNeeded("attackTarget", config.attackTarget());
        backupIfNeeded("strengthTarget", config.strengthTarget());
        backupIfNeeded("defenceTarget", config.defenceTarget());
        backupIfNeeded("rangedTarget", config.rangedTarget());
        backupIfNeeded("magicTarget", config.magicTarget());

        setTarget("attackTarget", 99);
        setTarget("strengthTarget", 99);
        setTarget("defenceTarget", 99);
        setTarget("rangedTarget", 99);
        setTarget("magicTarget", 99);
    }

    void restoreAfterRun()
    {
        restore("attackTarget");
        restore("strengthTarget");
        restore("defenceTarget");
        restore("rangedTarget");
        restore("magicTarget");
    }

    private void backupIfNeeded(String targetKey, int currentValue)
    {
        String backupKey = BACKUP_PREFIX + targetKey;
        Integer existing = configManager.getConfiguration(KspAioFighterConfig.GROUP, backupKey, Integer.class);
        if (existing == null)
        {
            configManager.setConfiguration(KspAioFighterConfig.GROUP, backupKey, currentValue);
        }
    }

    private void restore(String targetKey)
    {
        String backupKey = BACKUP_PREFIX + targetKey;
        Integer value = configManager.getConfiguration(KspAioFighterConfig.GROUP, backupKey, Integer.class);
        if (value == null) return;
        setTarget(targetKey, value);
        configManager.unsetConfiguration(KspAioFighterConfig.GROUP, backupKey);
    }

    private void setTarget(String key, int value)
    {
        configManager.setConfiguration(KspAioFighterConfig.GROUP, key, Math.max(1, Math.min(99, value)));
    }
}

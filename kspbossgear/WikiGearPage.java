package net.runelite.client.plugins.microbot.kspbossgear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class WikiGearPage
{
    private final String bossName;
    private final String pageName;
    private final String sourceUrl;
    private final List<GearMethod> methods;

    WikiGearPage(String bossName, String pageName, String sourceUrl, List<GearMethod> methods)
    {
        this.bossName = bossName;
        this.pageName = pageName;
        this.sourceUrl = sourceUrl;
        this.methods = Collections.unmodifiableList(new ArrayList<>(methods));
    }

    String getBossName()
    {
        return bossName;
    }

    String getPageName()
    {
        return pageName;
    }

    String getSourceUrl()
    {
        return sourceUrl;
    }

    List<GearMethod> getMethods()
    {
        return methods;
    }

    GearMethod findMethod(String name)
    {
        if (name == null) return methods.isEmpty() ? null : methods.get(0);
        for (GearMethod method : methods)
        {
            if (method.getName().equalsIgnoreCase(name)) return method;
        }
        return methods.isEmpty() ? null : methods.get(0);
    }

    static final class GearMethod
    {
        private final String name;
        private final List<GearRow> rows;

        GearMethod(String name, List<GearRow> rows)
        {
            this.name = name;
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        }

        String getName()
        {
            return name;
        }

        List<GearRow> getRows()
        {
            return rows;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    static final class GearRow
    {
        private final GearSlot slot;
        private final List<List<String>> columns;

        GearRow(GearSlot slot, List<List<String>> columns)
        {
            this.slot = slot;
            List<List<String>> copy = new ArrayList<>();
            for (List<String> column : columns)
            {
                copy.add(Collections.unmodifiableList(new ArrayList<>(column)));
            }
            this.columns = Collections.unmodifiableList(copy);
        }

        GearSlot getSlot()
        {
            return slot;
        }

        List<List<String>> getColumns()
        {
            return columns;
        }
    }
}

package net.runelite.client.plugins.microbot.kspbankorganizer;

public enum BankTarget {
    IGNORE("Ignore",-1), MAIN("Main tab",0), TAB_1("Tab 1",1), TAB_2("Tab 2",2), TAB_3("Tab 3",3), TAB_4("Tab 4",4), TAB_5("Tab 5",5), TAB_6("Tab 6",6), TAB_7("Tab 7",7), TAB_8("Tab 8",8), TAB_9("Tab 9",9);
    private final String displayName; private final int tabIndex;
    BankTarget(String name,int tab){displayName=name;tabIndex=tab;}
    public int getTabIndex(){return tabIndex;}
    @Override public String toString(){return displayName;}
}

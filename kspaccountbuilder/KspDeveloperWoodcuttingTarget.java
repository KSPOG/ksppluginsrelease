/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treeareas.TreeAreas;

public enum KspDeveloperWoodcuttingTarget {
    DEFAULT_PROGRESS(null),
    REGULAR_TREE_VARROCK_WEST(TreeAreas.REGULAR_TREE_VARROCK_WEST),
    OAK_TREE_DRAYNOR(TreeAreas.OAK_TREE_DRAYNOR),
    VCASTLE_OAKS(TreeAreas.VCASTLE_OAKS),
    VWEST_OAKS(TreeAreas.VWEST_OAKS),
    VEAST_OAKS(TreeAreas.VEAST_OAKS),
    WILLOW_TREES_DRAYNOR(TreeAreas.WILLOW_TREES_DRAYNOR),
    YEW_TREE_VARROCK_PALACE(TreeAreas.YEW_TREE_VARROCK_PALACE);

    private final TreeAreas treeArea;

    private KspDeveloperWoodcuttingTarget(TreeAreas treeArea) {
        this.treeArea = treeArea;
    }

    public TreeAreas getTreeArea() {
        return this.treeArea;
    }
}


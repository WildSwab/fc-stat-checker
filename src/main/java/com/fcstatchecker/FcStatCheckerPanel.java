package com.fcstatchecker;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.ColorScheme;

public class FcStatCheckerPanel extends PluginPanel
{
    private static final String[] SKILL_NAMES = {
            "Total Level", "Attack", "Defence", "Strength", "Hitpoints",
            "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
            "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing",
            "Mining", "Herblore", "Agility", "Thieving", "Slayer",
            "Farming", "Runecraft", "Hunter", "Construction"
    };

    private final Client client;
    private final HiscoreClient hiscoreClient;

    private final JComboBox<String> skillDropdown;
    private final JSpinner levelSpinner;
    private final JButton searchButton;
    private final JToggleButton unrankedOnlyCheckbox;
    private final JPanel resultsPanel;
    private final JLabel statusLabel;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final List<String> memberNames = new ArrayList<>();

    @Inject
    public FcStatCheckerPanel(Client client, HiscoreClient hiscoreClient)
    {
        this.client = client;
        this.hiscoreClient = hiscoreClient;

        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new GridLayout(0, 1, 0, 6));
        controlsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(1000, 38, 2376, 1);
        levelSpinner = new JSpinner(spinnerModel);
        ((JSpinner.DefaultEditor) levelSpinner.getEditor()).getTextField().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        ((JSpinner.DefaultEditor) levelSpinner.getEditor()).getTextField().setForeground(Color.WHITE);

        skillDropdown = new JComboBox<>(SKILL_NAMES);
        skillDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        skillDropdown.setForeground(Color.WHITE);
        skillDropdown.addActionListener(e ->
        {
            String selected = (String) skillDropdown.getSelectedItem();
            SpinnerNumberModel model = (SpinnerNumberModel) levelSpinner.getModel();
            if ("Total Level".equals(selected))
            {
                model.setMinimum(38);
                model.setMaximum(2376);
                if ((int) model.getValue() > 2376) model.setValue(2376);
                if ((int) model.getValue() < 38) model.setValue(38);
            }
            else
            {
                model.setMinimum(1);
                model.setMaximum(99);
                if ((int) model.getValue() > 99) model.setValue(99);
                if ((int) model.getValue() < 1) model.setValue(1);
            }
        });

        JLabel skillLabel = new JLabel("Skill:");
        skillLabel.setForeground(Color.WHITE);
        JLabel levelLabel = new JLabel("Minimum Level:");
        levelLabel.setForeground(Color.WHITE);

        controlsPanel.add(skillLabel);
        controlsPanel.add(skillDropdown);
        controlsPanel.add(levelLabel);
        controlsPanel.add(levelSpinner);

        UIManager.put("ToggleButton.select", ColorScheme.DARKER_GRAY_COLOR);
        unrankedOnlyCheckbox = new JToggleButton("Unranked only: OFF");
        unrankedOnlyCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        unrankedOnlyCheckbox.setForeground(Color.WHITE);
        unrankedOnlyCheckbox.setFocusPainted(false);
        unrankedOnlyCheckbox.setOpaque(true);
        unrankedOnlyCheckbox.addActionListener(e ->
                unrankedOnlyCheckbox.setText(unrankedOnlyCheckbox.isSelected()
                        ? "Unranked only: ON"
                        : "Unranked only: OFF"));
        controlsPanel.add(unrankedOnlyCheckbox);

        searchButton = new JButton("Search FC");
        searchButton.setBackground(ColorScheme.BRAND_ORANGE);
        searchButton.setForeground(Color.WHITE);
        searchButton.setFocusPainted(false);
        searchButton.addActionListener(e -> runSearch());
        controlsPanel.add(searchButton);

        statusLabel = new JLabel("Click Search FC to begin.");
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        controlsPanel.add(statusLabel);

        add(controlsPanel, BorderLayout.NORTH);

        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(resultsPanel);
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        add(scrollPane, BorderLayout.CENTER);
    }

    private void runSearch()
    {
        memberNames.clear();
        resultsPanel.removeAll();
        revalidate();
        repaint();

        FriendsChatManager fcm = client.getFriendsChatManager();

        if (fcm == null || fcm.getMembers() == null || fcm.getMembers().length == 0)
        {
            statusLabel.setText("Not in a Friends Chat.");
            revalidate();
            repaint();
            return;
        }

        for (FriendsChatMember member : fcm.getMembers())
        {
            memberNames.add(member.getName());
        }

        String selectedSkill = (String) skillDropdown.getSelectedItem();
        int minLevel = (int) levelSpinner.getValue();

        searchButton.setEnabled(false);
        statusLabel.setText("Checking 0 / " + memberNames.size() + "...");

        scheduleNextLookup(0, selectedSkill, minLevel, new ArrayList<>(memberNames), new int[]{0}, new int[]{0});
    }

    private void scheduleNextLookup(int index, String selectedSkill, int minLevel,
                                    List<String> names, int[] checked, int[] failed)
    {
        if (index >= names.size())
        {
            SwingUtilities.invokeLater(() ->
            {
                String suffix = unrankedOnlyCheckbox.isSelected()
                        ? failed[0] + " unranked member(s) found."
                        : "Done! " + failed[0] + " / " + names.size() + " don't meet req.";
                statusLabel.setText(suffix);
                searchButton.setEnabled(true);
            });
            return;
        }

        String name = names.get(index);

        executor.schedule(() ->
        {
            String levelText;
            Color rowColor;
            boolean addRow = true;

            try
            {
                HiscoreResult result = hiscoreClient.lookup(name, HiscoreEndpoint.NORMAL);

                if (result == null)
                {
                    if (unrankedOnlyCheckbox.isSelected())
                    {
                        addRow = false;
                    }
                    levelText = "Not found";
                    rowColor = Color.GRAY;
                }
                else
                {
                    HiscoreSkill hiscoreSkill = toHiscoreSkill(selectedSkill);
                    int level = result.getSkill(hiscoreSkill).getLevel();
                    boolean isUnranked = (level == -1);

                    if (unrankedOnlyCheckbox.isSelected() && !isUnranked)
                    {
                        addRow = false;
                        levelText = "";
                        rowColor = Color.GRAY;
                    }
                    else if (isUnranked)
                    {
                        levelText = "Unranked";
                        rowColor = new Color(200, 150, 50);
                        failed[0]++;
                    }
                    else
                    {
                        boolean meetsReq = level >= minLevel;
                        if (meetsReq)
                        {
                            addRow = false;
                            levelText = "";
                            rowColor = Color.GRAY;
                        }
                        else
                        {
                            levelText = level + "";
                            rowColor = new Color(200, 50, 50);
                            failed[0]++;
                        }
                    }
                }
            }
            catch (Exception e)
            {
                levelText = "Error";
                rowColor = Color.GRAY;
                if (unrankedOnlyCheckbox.isSelected())
                {
                    addRow = false;
                }
            }

            checked[0]++;
            final int currentChecked = checked[0];
            final boolean shouldAddRow = addRow;
            final String finalLevelText = levelText;
            final Color finalColor = rowColor;

            SwingUtilities.invokeLater(() ->
            {
                if (shouldAddRow)
                {
                    resultsPanel.add(createMemberRow(name, finalLevelText, finalColor));
                }
                statusLabel.setText("Checking " + currentChecked + " / " + names.size() + "...");
                revalidate();
                repaint();
            });

            scheduleNextLookup(index + 1, selectedSkill, minLevel, names, checked, failed);

        }, 600, TimeUnit.MILLISECONDS);
    }

    private JPanel createMemberRow(String name, String levelText, Color textColor)
    {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(4, 6, 4, 6));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setForeground(Color.WHITE);

        JLabel lvlLabel = new JLabel(levelText);
        lvlLabel.setForeground(textColor);
        lvlLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(nameLabel, BorderLayout.WEST);
        row.add(lvlLabel, BorderLayout.EAST);

        return row;
    }

    private HiscoreSkill toHiscoreSkill(String skillName)
    {
        switch (skillName)
        {
            case "Total Level": return HiscoreSkill.OVERALL;
            case "Attack": return HiscoreSkill.ATTACK;
            case "Defence": return HiscoreSkill.DEFENCE;
            case "Strength": return HiscoreSkill.STRENGTH;
            case "Hitpoints": return HiscoreSkill.HITPOINTS;
            case "Ranged": return HiscoreSkill.RANGED;
            case "Prayer": return HiscoreSkill.PRAYER;
            case "Magic": return HiscoreSkill.MAGIC;h
            case "Cooking": return HiscoreSkill.COOKING;
            case "Woodcutting": return HiscoreSkill.WOODCUTTING;
            case "Fletching": return HiscoreSkill.FLETCHING;
            case "Fishing": return HiscoreSkill.FISHING;
            case "Firemaking": return HiscoreSkill.FIREMAKING;
            case "Crafting": return HiscoreSkill.CRAFTING;
            case "Smithing": return HiscoreSkill.SMITHING;
            case "Mining": return HiscoreSkill.MINING;
            case "Herblore": return HiscoreSkill.HERBLORE;
            case "Agility": return HiscoreSkill.AGILITY;
            case "Thieving": return HiscoreSkill.THIEVING;
            case "Slayer": return HiscoreSkill.SLAYER;
            case "Farming": return HiscoreSkill.FARMING;
            case "Runecraft": return HiscoreSkill.RUNECRAFT;
            case "Hunter": return HiscoreSkill.HUNTER;
            case "Construction": return HiscoreSkill.CONSTRUCTION;
            default: return HiscoreSkill.OVERALL;
        }
    }
}
package com.krovex.launcher.ui.components;

import com.krovex.launcher.auth.AccountManager;
import com.krovex.launcher.model.MicrosoftAccount;
import com.krovex.launcher.ui.LauncherStyle;
import com.krovex.launcher.ui.screens.SignInDialog;
import com.krovex.theme.KrovexTheme;
import java.awt.*;
import javax.swing.*;

public final class AccountSidebarPanel extends JPanel {
    private final KrovexTheme theme;
    private final AccountManager accounts;
    private final Runnable changed;

    public AccountSidebarPanel(KrovexTheme theme, AccountManager accounts, Runnable changed) {
        super(new BorderLayout());
        this.theme = theme;
        this.accounts = accounts;
        this.changed = changed;
        setBackground(LauncherStyle.BASE);
        setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 14));
        setPreferredSize(new Dimension(294, 0));
        rebuild();
    }

    public void rebuild() {
        removeAll();
        JPanel card = LauncherStyle.card(new BorderLayout(), 14);
        JLabel heading = new JLabel("Playing as");
        heading.setForeground(LauncherStyle.MUTED);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        JPanel items = new JPanel();
        items.setOpaque(false);
        items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        card.add(items);
        items.add(heading);
        items.add(Box.createVerticalStrut(8));
        var active = accounts.activeAccount().orElse(null);
        String username = active == null ? "Player" : active.minecraftUsername();
        String state = active == null || active.isOffline() ? "Offline" : "Microsoft account";
        var select = LauncherStyle.button("", false, () -> {});
        select.getAccessibleContext().setAccessibleName(username + ", " + state + ", choose account");
        select.setLayout(new BorderLayout(12, 0));
        select.setBorder(BorderFactory.createCompoundBorder(LauncherStyle.outline(19),
                BorderFactory.createEmptyBorder(9, 13, 9, 13)));
        if (active != null && !active.isOffline()) AccountAvatar.load(active, select);
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        JLabel name = new JLabel(username);
        name.putClientProperty("html.disable", true);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 19f));
        name.setForeground(LauncherStyle.INK);
        text.add(name);
        JLabel subtitle = new JLabel("●  " + state);
        subtitle.setForeground(LauncherStyle.MUTED);
        text.add(subtitle);
        select.add(text, BorderLayout.CENTER);
        if (active != null) {
            select.add(new JLabel(com.krovex.launcher.ui.LauncherComboBoxUI.chevron()), BorderLayout.EAST);
        } else {
            select.setEnabled(false);
        }
        select.setAlignmentX(LEFT_ALIGNMENT);
        select.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        items.add(select);
        if (active != null) {
            JPanel disclosure = new JPanel(new BorderLayout());
            disclosure.setOpaque(false);
            disclosure.setAlignmentX(LEFT_ALIGNMENT);
            var remove = LauncherStyle.button("Remove account", false, () -> confirmRemoval(active));
            remove.setIcon(AccountAvatar.removeIcon());
            disclosure.add(remove);
            JPanel others = new JPanel(new GridLayout(0, 1, 0, 4));
            others.setOpaque(false);
            for (var account : accounts.accounts()) {
                if (account.accountId().equals(active.accountId())) continue;
                others.add(LauncherStyle.button("Switch to " + account.minecraftUsername(), false, () -> {
                    accounts.setActive(account);
                    rebuild();
                    changed.run();
                }));
            }
            if (others.getComponentCount() > 0) disclosure.add(others, BorderLayout.SOUTH);
            int height = disclosure.getPreferredSize().height;
            disclosure.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
            disclosure.setVisible(false);
            items.add(disclosure);
            select.addActionListener(event -> AccountAvatar.slide(disclosure, height));
        }
        items.add(Box.createVerticalStrut(12));
        var add = LauncherStyle.button("+  Add account", true, this::signIn);
        add.setAlignmentX(LEFT_ALIGNMENT);
        add.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        items.add(add);
        add(card, BorderLayout.NORTH);
        revalidate();
        repaint();
    }

    private void confirmRemoval(MicrosoftAccount account) {
        var root = SwingUtilities.getRootPane(this);
        if (root == null) return;
        var previous = root.getGlassPane();
        boolean wasVisible = previous.isVisible();
        var focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        JPanel overlay = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                graphics.setColor(new Color(0, 0, 0, 155));
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        overlay.setOpaque(false);
        overlay.addMouseListener(new java.awt.event.MouseAdapter() {});
        Runnable close = () -> {
            overlay.setVisible(false);
            root.setGlassPane(previous);
            previous.setVisible(wasVisible);
            if (focus != null) focus.requestFocusInWindow();
        };
        JPanel card = LauncherStyle.card(new BorderLayout(0, 16), 20);
        card.add(new JLabel("Are you sure you would like to remove your account?"), BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        var cancel = LauncherStyle.button("Cancel", false, close);
        controls.add(LauncherStyle.button("Yes", true, () -> {
            close.run();
            accounts.signOut(account);
            rebuild();
            changed.run();
        }));
        controls.add(cancel);
        card.add(controls, BorderLayout.SOUTH);
        overlay.add(card);
        overlay.registerKeyboardAction(event -> close.run(), KeyStroke.getKeyStroke("ESCAPE"), WHEN_IN_FOCUSED_WINDOW);
        overlay.setFocusCycleRoot(true);
        root.setGlassPane(overlay);
        overlay.setVisible(true);
        cancel.requestFocusInWindow();
    }

    private void signIn() {
        if (!(SwingUtilities.getWindowAncestor(this) instanceof Frame owner)) return;
        new SignInDialog(owner, theme, accounts, account -> {
            accounts.setActive(account);
            rebuild();
            changed.run();
        }).setVisible(true);
    }
}

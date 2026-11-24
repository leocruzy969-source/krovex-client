package com.krovex.launcher.ui.screens;

import com.krovex.launcher.auth.AccountManager;
import com.krovex.launcher.model.MicrosoftAccount;
import com.krovex.launcher.ui.SwingThemeUtil;
import com.krovex.launcher.ui.LauncherStyle;
import com.krovex.theme.KrovexTheme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

public final class SignInDialog
extends JDialog {
    public SignInDialog(Frame owner, KrovexTheme theme, final AccountManager accountManager, final Consumer<MicrosoftAccount> onSignedIn) {
        super(owner, "Add Account", true);
        this.getContentPane().setBackground(SwingThemeUtil.background(theme));
        this.setLayout(new BorderLayout());
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setUndecorated(true);
        getRootPane().setBorder(LauncherStyle.outline(14));
        getRootPane().registerKeyboardAction(event -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        com.krovex.launcher.ui.MockupChrome.dimOwner(this);
        this.setSize(545, 506);
        this.setLocationRelativeTo(owner);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, 1));
        content.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JPanel heading = new JPanel(new BorderLayout(8, 0));
        heading.setOpaque(false);
        heading.setAlignmentX(LEFT_ALIGNMENT);
        heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        JLabel title = new JLabel("Add account");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 30f));
        heading.add(title, BorderLayout.WEST);
        heading.add(LauncherStyle.button("Close", false, this::dispose), BorderLayout.EAST);
        content.add(heading);
        content.add(Box.createVerticalStrut(9));
        JLabel intro = new JLabel("Sign in with the Microsoft account that owns Minecraft: Java Edition.");
        intro.setForeground(LauncherStyle.MUTED);
        intro.setAlignmentX(LEFT_ALIGNMENT);
        content.add(intro);
        content.add(Box.createVerticalStrut(22));
        final JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(SwingThemeUtil.subtleText(theme));
        statusLabel.setFont(SwingThemeUtil.baseFont(theme, 0));
        statusLabel.setAlignmentX(0.0f);
        final JButton microsoftButton = LauncherStyle.button("Sign in with Microsoft", true, () -> {});
        boolean microsoftAvailable = accountManager.canSignInWithMicrosoft();
        microsoftButton.setEnabled(microsoftAvailable);
        microsoftButton.setAlignmentX(LEFT_ALIGNMENT);
        microsoftButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        microsoftButton.addActionListener(e -> {
            microsoftButton.setEnabled(false);
            statusLabel.setText("Choose your Microsoft account in your browser…");
            new SwingWorker<MicrosoftAccount, Void>(){

                @Override
                protected MicrosoftAccount doInBackground() throws Exception {
                    return SignInDialog.this.accountManagerFieldSignIn(accountManager);
                }

                @Override
                protected void done() {
                    try {
                        MicrosoftAccount account = (MicrosoftAccount)this.get();
                        onSignedIn.accept(account);
                        SignInDialog.this.dispose();
                    }
                    catch (Exception ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        statusLabel.setText(" ");
                        javax.swing.JOptionPane.showMessageDialog(SignInDialog.this, cause.getMessage(),
                                "Microsoft sign-in", javax.swing.JOptionPane.WARNING_MESSAGE);
                        microsoftButton.setEnabled(true);
                    }
                }
            }.execute();
        });
        JLabel subtitle = new JLabel(microsoftAvailable
                ? "Choose your account on Microsoft’s sign-in page."
                : "Microsoft sign-in is unavailable in this build.");
        subtitle.setForeground(SwingThemeUtil.subtleText(theme));
        subtitle.setFont(SwingThemeUtil.baseFont(theme, 0));
        subtitle.setAlignmentX(0.0f);
        JLabel microsoftHeading = new JLabel("Microsoft account",
                com.krovex.launcher.ui.MockupChrome.icon("microsoft", 30), JLabel.LEFT);
        microsoftHeading.setFont(microsoftHeading.getFont().deriveFont(java.awt.Font.BOLD, 21f));
        microsoftHeading.setAlignmentX(LEFT_ALIGNMENT);
        content.add(microsoftHeading);
        content.add(Box.createVerticalStrut(5));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(12));
        content.add(microsoftButton);
        content.add(Box.createVerticalStrut(20));
        content.add(statusLabel);
        this.add((Component)content, "Center");
    }

    private MicrosoftAccount accountManagerFieldSignIn(AccountManager accountManager) throws Exception {
        return accountManager.signInNewAccount();
    }
}


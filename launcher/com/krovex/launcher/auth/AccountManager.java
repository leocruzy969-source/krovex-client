package com.krovex.launcher.auth;

import com.krovex.launcher.auth.CredentialStore;
import com.krovex.launcher.auth.MicrosoftAuthFlow;
import com.krovex.launcher.model.AccountStore;
import com.krovex.launcher.model.MicrosoftAccount;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class AccountManager {
    private final AccountStore accountStore;
    private final CredentialStore credentialStore;
    private final MicrosoftAuthFlow authFlow;

    public AccountManager(Path krovexHome, String azureClientId) {
        this.accountStore = new AccountStore(krovexHome);
        this.credentialStore = new CredentialStore(krovexHome);
        this.authFlow = new MicrosoftAuthFlow(azureClientId, this.credentialStore);
        this.accountStore.load();
    }

    public List<MicrosoftAccount> accounts() {
        return this.accountStore.all();
    }

    public Optional<MicrosoftAccount> activeAccount() {
        return this.accountStore.active();
    }

    public void setActive(MicrosoftAccount account) {
        this.accountStore.setActive(account);
    }

    public boolean canSignInWithMicrosoft() {
        return authFlow.isConfigured();
    }

    public MicrosoftAccount signInNewAccount() throws MicrosoftAuthFlow.AuthException {
        MicrosoftAccount account = this.authFlow.signInInteractive();
        this.accountStore.addOrUpdate(account);
        return account;
    }

    public MicrosoftAccount addOfflineAccount(String username) {
        MicrosoftAccount account = MicrosoftAccount.createOffline(username);
        this.accountStore.addOrUpdate(account);
        return account;
    }

    public MicrosoftAccount ensureFreshToken(MicrosoftAccount account) throws MicrosoftAuthFlow.AuthException {
        if (account.isOffline() || account.accessTokenValid()) {
            return account;
        }
        MicrosoftAccount refreshed = this.authFlow.refresh(account);
        this.accountStore.addOrUpdate(refreshed);
        return refreshed;
    }

    public void signOut(MicrosoftAccount account) {
        if (!account.isOffline()) {
            try {
                this.credentialStore.deleteRefreshToken(account.accountId());
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        this.accountStore.signOut(account);
    }
}


package ru.vidtu.ias.account;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ru.vidtu.ias.SharedIAS;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Token-based account for Minecraft.
 * Uses a pre-existing access token for authentication.
 *
 * @author VidTu (base structure)
 * @author Barry (token implementation)
 */
public class TokenAccount implements Account {
    private String name;
    private String accessToken;
    private UUID uuid;
    private long addedTime; // When token was added (for estimating expiry)

    public TokenAccount(@NotNull String accessToken) {
        this.name = "Token Account"; // Placeholder, will be updated on login
        this.accessToken = accessToken;
        this.uuid = UUID.randomUUID(); // Placeholder
        this.addedTime = System.currentTimeMillis();
    }

    public TokenAccount(@NotNull String name, @NotNull String accessToken, @NotNull UUID uuid, long addedTime) {
        this.name = name;
        this.accessToken = accessToken;
        this.uuid = uuid;
        this.addedTime = addedTime;
    }

    @Override
    public @NotNull UUID uuid() {
        return uuid;
    }

    @Override
    public @NotNull String name() {
        return name;
    }

    /**
     * Get access token of this account.
     *
     * @return Access token
     */
    @Contract(pure = true)
    public @NotNull String accessToken() {
        return accessToken;
    }

    /**
     * Get time when token was added.
     *
     * @return Added timestamp in milliseconds
     */
    @Contract(pure = true)
    public long addedTime() {
        return addedTime;
    }

    /**
     * Check if token is likely expired (>24 hours old).
     *
     * @return true if likely expired, false otherwise
     */
    @Contract(pure = true)
    public boolean isLikelyExpired() {
        long elapsed = System.currentTimeMillis() - addedTime;
        return elapsed >= (24 * 60 * 60 * 1000); // 24 hours
    }

    /**
     * Get estimated time remaining until expiry.
     *
     * @return Time remaining string
     */
    @Contract(pure = true)
    public @NotNull String getTimeRemaining() {
        long elapsed = System.currentTimeMillis() - addedTime;
        long remaining = (24 * 60 * 60 * 1000) - elapsed; // 24 hours - elapsed

        if (remaining <= 0) return "Likely Expired";

        long hours = remaining / (1000 * 60 * 60);
        long minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60);

        if (hours > 0) {
            return "~" + hours + "h " + minutes + "m";
        } else {
            return "~" + minutes + "m";
        }
    }

    @Override
    public @NotNull CompletableFuture<@NotNull AuthData> login(@NotNull BiConsumer<@NotNull String, @NotNull Object[]> progressHandler) {
        CompletableFuture<AuthData> cf = new CompletableFuture<>();
        SharedIAS.EXECUTOR.execute(() -> {
            try {
                // Validate token by getting profile
                progressHandler.accept("ias.loginGui.token.validating", new Object[]{});
                Map.Entry<UUID, String> profile = Auth.getProfile(accessToken);

                // Update name and UUID from validation
                this.uuid = profile.getKey();
                this.name = profile.getValue();

                SharedIAS.LOG.info("Token validated successfully for: " + name);
                cf.complete(new AuthData(name, uuid, accessToken, AuthData.MSA));
            } catch (Throwable t) {
                SharedIAS.LOG.error("Unable to login with token.", t);
                cf.completeExceptionally(t);
            }
        });
        return cf;
    }
}